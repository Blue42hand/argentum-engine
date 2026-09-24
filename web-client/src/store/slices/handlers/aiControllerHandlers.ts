import type { MessageHandlers } from '@/network/messageHandlers'
import { receiveAiControllerCatalog } from '@/store/aiControllerStore'

export function createAiControllerHandlers(): Pick<MessageHandlers, 'onAiControllerCatalog'> {
  return {
    onAiControllerCatalog: receiveAiControllerCatalog,
  }
}
