import { createContext } from 'react';
import { Send } from '../webSocket';

export const SendContext = createContext<Send | undefined>(undefined);
