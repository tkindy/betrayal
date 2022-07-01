import { createAction } from '@reduxjs/toolkit';
import { AppThunk } from '../store';
import * as lobbyActions from './lobby';
import { Card, DiceRoll, Monster, Player, Room } from './models';
import { RoomStackState } from './roomStack';

export const joinGame = createAction<{ gameId: string }>('game/join');

interface PlayersMessage {
  type: 'players';
  players: string[];
}

interface JoinGameMessage {
  type: 'join';
}

type LobbyServerMessage = PlayersMessage | JoinGameMessage;

export const receiveLobbyMessage =
  (message: LobbyServerMessage): AppThunk =>
  (dispatch) => {
    switch (message.type) {
      case 'players':
        dispatch(lobbyActions.setPlayers(message.players));
        break;
      case 'join':
        dispatch(lobbyActions.switchToGame());
        break;
    }
  };

interface GameStateMessage {
  type: 'state';
  rooms: Room[];
  players: Player[];
  roomStack: RoomStackState;
  drawnCard: Card | null;
  latestRoll: DiceRoll | null;
  monsters: Monster[];
}

interface CardStackContentsMessage {
  type: 'card-stack-contents';
  contents: Card[];
}

export type GameServerMessage = GameStateMessage | CardStackContentsMessage;

interface GameServerMessageMeta {
  name: string;
}

interface AnyGameServerMessagePayload extends GameServerMessageMeta {
  message: GameServerMessage;
}

interface GameServerMessagePayload<T extends GameServerMessage>
  extends GameServerMessageMeta {
  message: T;
}
export const receiveGameStateMessage =
  createAction<GameServerMessagePayload<GameStateMessage>>('game/receiveState');

export const receiveCardStackContentsMessage = createAction<
  GameServerMessagePayload<CardStackContentsMessage>
>('game/receiveCardStackContents');

export const receiveGameMessage =
  ({ message, ...rest }: AnyGameServerMessagePayload): AppThunk =>
  (dispatch) => {
    switch (message.type) {
      case 'state':
        dispatch(receiveGameStateMessage({ message, ...rest }));
        break;
      case 'card-stack-contents':
        dispatch(receiveCardStackContentsMessage({ message, ...rest }));
        break;
    }
  };
