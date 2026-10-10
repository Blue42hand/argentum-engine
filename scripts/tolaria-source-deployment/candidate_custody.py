"""Root custody and descriptor-anchored ingestion of untrusted build artifacts.

No subprocesses/network or source scripts run here. Rejected snapshots are kept.
Only the selected result/JAR/frontend bundle is ingested, never build scratch.
"""
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import uuid
from coordinator_validation import canonical, digest, require, validate_job, validate_result


ROOT_UID = 0

def unique_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite JSON')))


def identity(info):
    return dict(device=info.st_dev, inode=info.st_ino, mode=stat.S_IMODE(info.st_mode),
        uid=info.st_uid, gid=info.st_gid, size=info.st_size, mtime_ns=info.st_mtime_ns, ctime_ns=info.st_ctime_ns)


def require_root():
    require(os.geteuid() == 0, 'root coordinator required')


def trusted(path, directory=False):
    path = Path(path)
    require(path.is_absolute(), 'absolute root custody path required')
    for item in (path, *path.parents):
        info = item.lstat()
        kind = stat.S_ISDIR if item != path or directory else stat.S_ISREG
        require(kind(info.st_mode) and info.st_uid == ROOT_UID and not info.st_mode & 0o022,
            'root custody/nonwritable ancestry required')


class Anchor:
    """Keep every traversed ancestor/directory FD until final path revalidation."""
    def __init__(self, root, *, boundary, owner_uid):
        self.root = Path(root); self.boundary = Path(boundary); self.owner_uid = owner_uid
        require(self.root.is_absolute() and self.root.is_relative_to(self.boundary), 'bounded attempt path required')
        trusted(self.boundary, True)
        self.fds = {}; self.links = {}; self.closed = False
        try:
            fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            self.fds['/'] = fd
            key = '/'
            for component in self.root.parts[1:]:
                parent = key; key = str(Path(parent) / component)
                fd = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=self.fds[parent])
                self.fds[key] = fd; self.links[key] = (parent, component, identity(os.fstat(fd)))
                if Path(key).is_relative_to(self.boundary) and Path(key) != self.boundary:
                    self._shape(os.fstat(fd), True)
            self.base_fd = self.fds[str(self.root)]
        except BaseException:
            self.close(); raise

    def _shape(self, info, directory):
        require((stat.S_ISDIR if directory else stat.S_ISREG)(info.st_mode) and
            info.st_uid == self.owner_uid and not info.st_mode & 0o022 and
            (directory or info.st_nlink == 1), 'untrusted bundle shape/owner held')

    def open(self, relative, directory=False):
        rel = PurePosixPath(relative)
        require(type(relative) is str and relative and not rel.is_absolute() and
            '..' not in rel.parts and '.' not in rel.parts and '\x00' not in relative and
            rel.as_posix() == relative, 'canonical relative artifact path required')
        parent = str(self.root)
        for offset, component in enumerate(rel.parts):
            key = str(Path(parent) / component)
            is_dir = directory or offset < len(rel.parts) - 1
            if key not in self.fds:
                flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | (os.O_DIRECTORY if is_dir else 0)
                fd = os.open(component, flags, dir_fd=self.fds[parent])
                self.fds[key] = fd; self.links[key] = (parent, component, identity(os.fstat(fd)))
                self._shape(os.fstat(fd), is_dir)
            self._shape(os.fstat(self.fds[key]), is_dir)
            parent = key
        return self.fds[parent]

    def recheck(self, writable_directory=None):
        for key, (parent, name, pinned) in self.links.items():
            held = identity(os.fstat(self.fds[key]))
            named = identity(os.stat(name, dir_fd=self.fds[parent], follow_symlinks=False))
            # Directory sibling creation (e.g. /tmp or /var/lib) does not
            # replace any pinned object. Selected files retain full metadata.
            fields = ('device', 'inode', 'mode', 'uid', 'gid') if stat.S_ISDIR(os.fstat(self.fds[key]).st_mode) else tuple(pinned)
            require(all(held[k] == pinned[k] == named[k] for k in fields),
                'pinned ancestor/artifact replaced or changed')

    def close(self):
        if self.closed: return
        self.closed = True
        for fd in reversed(list(self.fds.values())): os.close(fd)
        self.fds.clear()

    def __enter__(self): return self
    def __exit__(self, *_): self.close()


def read_fd(fd, *, limit=512 * 1024**2):
    info = os.fstat(fd)
    require(stat.S_ISREG(info.st_mode) and 0 <= info.st_size <= limit, 'bounded regular artifact required')
    h = hashlib.sha256(); offset = 0
    while block := os.pread(fd, 1024 * 1024, offset):
        offset += len(block); require(offset <= limit, 'artifact grew beyond limit'); h.update(block)
    require(offset == info.st_size and identity(os.fstat(fd)) == identity(info), 'artifact changed during hash')
    return h.hexdigest(), offset


def root_read(path, *, limit=4 * 1024**2):
    trusted(path)
    path = Path(path)
    with Anchor(path.parent, boundary=path.parent, owner_uid=ROOT_UID) as anchor:
        fd = anchor.open(path.name)
        info = os.fstat(fd)
        require(info.st_size <= limit and not info.st_mode & 0o022, 'bounded root index required')
        raw = os.pread(fd, limit + 1, 0)
        require(len(raw) == info.st_size, 'root read changed size')
        anchor.recheck()
    return raw


def root_write(path, value):
    require_root(); path = Path(path); trusted(path.parent, True)
    # Existing members never get overwritten; an interrupted or rejected attempt stays.
    with Anchor(path.parent, boundary=path.parent, owner_uid=ROOT_UID) as anchor:
        fd = os.open(path.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=anchor.base_fd)
        try:
            os.fchmod(fd, 0o600)
            raw = canonical(value) + b'\n'
            with os.fdopen(fd, 'wb', closefd=False) as out:
                out.write(raw); out.flush(); os.fsync(fd)
            anchor.recheck(writable_directory=str(path.parent)); os.fsync(anchor.base_fd)
        finally: os.close(fd)
    return hashlib.sha256(raw).hexdigest()


def archive_index(path, role, source_sha, expected):
    """Rehash the actual immutable root cache; never trust an index's success bool."""
    require_root(); trusted(path)
    require(role in ('engine', 'gym') and Path(path).name == source_sha + '.tar.gz', 'exact role/source archive name required')
    with Anchor(Path(path).parent, boundary=Path(path).parent, owner_uid=ROOT_UID) as anchor:
        fd = anchor.open(Path(path).name); actual, size = read_fd(fd)
        anchor.recheck()
        require(actual == expected['sha256'] and size == expected['bytes'], 'archive bytes/job binding mismatch')
        return dict(schemaVersion=1, role=role, sourceSha=source_sha, archiveSha256=actual,
            archiveBytes=size, path=str(Path(path)), identity=identity(os.fstat(fd)))


def snapshot_bundle(anchor):
    files = {}; members = {}; total = 0
    def visit(relative):
        nonlocal total
        fd = anchor.open(relative, True); names = sorted(os.listdir(fd)); members[relative] = names
        require(len(files) + len(members) < 50000, 'bundle entry limit')
        for name in names:
            require(name not in ('.', '..') and '/' not in name and '\x00' not in name, 'bundle member name held')
            child = relative + '/' + name
            info = os.stat(name, dir_fd=fd, follow_symlinks=False)
            if stat.S_ISDIR(info.st_mode): visit(child)
            else: add(child)
    def add(relative):
        nonlocal total
        fd = anchor.open(relative); sha, size = read_fd(fd, limit=65536 if relative == 'result.json' else 512 * 1024**2)
        total += size; require(total <= 2 * 1024**3, 'bundle total limit')
        files[relative] = dict(identity(os.fstat(fd)), sha256=sha)
    add('result.json'); add('engine/game-server/build/libs/game-server.jar')
    visit('engine/web-client/dist')
    require('engine/web-client/dist/index.html' in files, 'frontend index absent')
    anchor.recheck()
    return dict(files=files, directories=members)


def frontend_digest(snapshot):
    h = hashlib.sha256(); prefix = 'engine/web-client/dist/'; count = 0
    for name, meta in sorted(snapshot['files'].items()):
        if name.startswith(prefix):
            h.update(name[len(prefix):].encode() + b'\0' + bytes.fromhex(meta['sha256'])); count += 1
    return h.hexdigest(), count


def copy_fd(source, destination):
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        os.fchmod(fd, 0o600); offset = 0
        with os.fdopen(fd, 'wb', closefd=False) as out:
            while block := os.pread(source, 1024 * 1024, offset):
                out.write(block); offset += len(block)
            out.flush(); os.fsync(fd)
    finally: os.close(fd)


def ingest_attempt(*, job_path, archive_paths, attempt_path, build_boundary, builder_uid,
                   retained_root, assert_builder_stopped):
    """Create a fresh retained root snapshot; reject, never delete, any failed attempt.

    The mandatory callback is a root executor's fresh supervisor assertion, not a
    field from builder output. Source archives are independently rehashed here.
    """
    require_root(); trusted(retained_root, True)
    require(type(builder_uid) is int and builder_uid != 0 and callable(assert_builder_stopped), 'unprivileged builder and supervisor assertion required')
    assert_builder_stopped()
    attempt_path = Path(attempt_path); require(attempt_path.parent == Path(build_boundary) / 'work', 'exact build work child required')
    destination = Path(retained_root) / ('attempt-' + uuid.uuid4().hex)
    destination.mkdir(mode=0o700)
    try:
        job_raw = root_read(job_path); job_identity = identity(Path(job_path).lstat()); job = unique_json(job_raw); job_id = validate_job(job)
        require(set(archive_paths) == {'engine', 'gym'}, 'both exact source archives required')
        archives = {role: archive_index(archive_paths[role], role, job[role+'Sha'], job['archives'][role]) for role in ('engine', 'gym')}
        with Anchor(attempt_path, boundary=build_boundary, owner_uid=builder_uid) as anchor:
            first = snapshot_bundle(anchor)
            result = unique_json(os.pread(anchor.open('result.json'), 65537, 0))
            require(first['files']['result.json']['size'] <= 65536, 'result JSON oversized')
            validate_result(result, job, attempt_path.name)
            jar = first['files']['engine/game-server/build/libs/game-server.jar']['sha256']
            web, count = frontend_digest(first)
            require((jar, web, count) == (result['jarSha256'], result['frontendTreeSha256'], result['frontendFiles']), 'builder artifact hashes do not match snapshot')
            for relative in sorted(first['directories']):
                (destination / 'bundle' / relative).mkdir(mode=0o700, parents=True, exist_ok=True)
            for relative in sorted(first['files']):
                target = destination / 'bundle' / relative
                target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                copy_fd(anchor.open(relative), target)
            # Every selected file rehashed, every directory re-enumerated and all
            # held ancestors checked after copying. No later source-path reopen.
            second = snapshot_bundle(anchor)
            require(second == first, 'full bundle snapshot changed during ingestion')
            anchor.recheck(); assert_builder_stopped()
            require(root_read(job_path) == job_raw and identity(Path(job_path).lstat()) == job_identity, 'installed build job changed during ingestion')
            for role in ('engine', 'gym'):
                require(archive_index(archive_paths[role], role, job[role+'Sha'], job['archives'][role]) == archives[role], 'source archive index changed')
            # Destination is independently rehashed, root custody, before sealing.
            copied = {}
            for relative, meta in first['files'].items():
                raw_path = destination / 'bundle' / relative; trusted(raw_path)
                with Anchor(raw_path.parent, boundary=raw_path.parent, owner_uid=ROOT_UID) as copied_anchor:
                    sha, size = read_fd(copied_anchor.open(raw_path.name)); copied_anchor.recheck()
                require((sha, size) == (meta['sha256'], meta['size']), 'root copy integrity held')
                copied[relative] = dict(sha256=sha, size=size)
            index = dict(schemaVersion=1, accepted=True, jobSha256=hashlib.sha256(job_raw).hexdigest(), jobId=job_id,
                job=job, archives=archives, builderResult=result, sourceSnapshot=first,
                sourceSnapshotSha256=digest(first), copiedFiles=copied, jarSha256=jar,
                frontendTreeSha256=web, retainedPath=str(destination))
            root_write(destination / 'index.json', index)
            return index
    except Exception as error:
        root_write(destination / 'rejected.json', dict(schemaVersion=1, accepted=False,
            errorType=type(error).__name__, attempt=attempt_path.name))
        raise


def validate_candidate_index(index_path):
    """Revalidate a sealed root index and its actual entire copied bundle."""
    require_root(); raw = root_read(index_path); value = unique_json(raw)
    expected = {'schemaVersion', 'accepted', 'jobSha256', 'jobId', 'job', 'archives',
        'builderResult', 'sourceSnapshot', 'sourceSnapshotSha256', 'copiedFiles',
        'jarSha256', 'frontendTreeSha256', 'retainedPath'}
    require(type(value) is dict and set(value) == expected and type(value['schemaVersion']) is int and
        value['schemaVersion'] == 1 and value['accepted'] is True and validate_job(value['job']) == value['jobId'] and
        digest(value['sourceSnapshot']) == value['sourceSnapshotSha256'], 'complete root candidate index required')
    require(Path(index_path).parent == Path(value['retainedPath']) and set(value['archives']) == {'engine', 'gym'}, 'retained root candidate location required')
    validate_result(value['builderResult'], value['job'], value['builderResult']['attempt'])
    projected = {name: {'sha256': meta['sha256'], 'size': meta['size']} for name, meta in value['sourceSnapshot']['files'].items()}
    require(projected == value['copiedFiles'], 'source/copy full snapshot binding held')
    bundle = Path(value['retainedPath']) / 'bundle'; trusted(bundle, True)
    with Anchor(bundle, boundary=bundle, owner_uid=ROOT_UID) as anchor:
        copied = snapshot_bundle(anchor)
        require({name: {'sha256': meta['sha256'], 'size': meta['size']} for name, meta in copied['files'].items()} == projected and
            copied['directories'] == value['sourceSnapshot']['directories'], 'root candidate bundle changed')
        # Reject every extra file/directory in the root snapshot, including scaffold.
        allowed = set(projected) | set(copied['directories'])
        allowed |= {str(parent) for name in list(allowed) for parent in PurePosixPath(name).parents if str(parent) != '.'}
        actual = set()
        def enumerate_all(relative=''):
            fd = anchor.base_fd if not relative else anchor.open(relative, True)
            for name in os.listdir(fd):
                child = name if not relative else relative + '/' + name
                require(child in allowed, 'unexpected root bundle member')
                actual.add(child)
                info = os.stat(name, dir_fd=fd, follow_symlinks=False)
                if stat.S_ISDIR(info.st_mode): enumerate_all(child)
                else: anchor.open(child)
        enumerate_all(); require(actual == allowed, 'incomplete root bundle members')
        anchor.recheck()
    jar = copied['files']['engine/game-server/build/libs/game-server.jar']['sha256']
    web, count = frontend_digest(copied)
    require((jar, web, count) == (value['jarSha256'], value['frontendTreeSha256'], value['builderResult']['frontendFiles']) and
        (jar, web) == (value['builderResult']['jarSha256'], value['builderResult']['frontendTreeSha256']), 'root artifact tuple mismatch')
    for role in ('engine', 'gym'):
        archive = value['archives'][role]
        require(archive_index(archive['path'], role, value['job'][role+'Sha'], value['job']['archives'][role]) == archive, 'root source archive changed')
    require(root_read(index_path) == raw, 'candidate index changed during verification')
    return value
