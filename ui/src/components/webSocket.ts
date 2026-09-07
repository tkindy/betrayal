import { AppDispatch } from '../store';

export type Send = (data: any) => void;

export const connectToWebSocket = <M>(
  url: string,
  dispatch: AppDispatch,
  messageActionCreator: (message: M) => Parameters<AppDispatch>[0],
  onOpen: (webSocket: WebSocket, e: Event) => void = () => {}
): { send: Send; close: () => void } => {
  let webSocket = new WebSocket(url);
  webSocket.onopen = (e) => {
    onOpen(webSocket, e);
  };
  webSocket.onmessage = (event) => {
    dispatch(messageActionCreator(JSON.parse(event.data)));
  };

  return {
    send: (data: any) => webSocket.send(JSON.stringify(data)),
    close: () => webSocket.close(),
  };
};
