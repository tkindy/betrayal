import { useCallback, useState } from 'react';
import { Send } from './webSocket';

export const useRender = () => {
  const [value, setValue] = useState(0);
  return () => {
    setValue(value + 1);
  };
};

export const useSend = (): [Send | undefined, (send?: Send) => void] => {
  const [wrappedSend, setWrappedSend] = useState<{ send: Send } | undefined>();
  return [
    wrappedSend?.send,
    useCallback(
      (send?: Send) => setWrappedSend(send ? { send } : undefined),
      [setWrappedSend]
    ),
  ];
};
