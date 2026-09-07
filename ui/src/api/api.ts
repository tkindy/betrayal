import { GridLoc } from '../components/game/board/grid';
import {
  DiceRoll,
  DiceRollType,
  EventCard,
  FlippedRoom,
  ItemCard,
  Monster,
  OmenCard,
  Player,
  Room,
  StackRoom,
  TraitName,
} from '../features/models';
import axios from 'axios';

const buildApiUrl = (path: string) => process.env.REACT_APP_API_ROOT + path;

export const createLobby: (hostName: string) => Promise<string> = async (
  hostName
) => {
  const response = await axios.post<{ id: string }>(buildApiUrl('/lobbies'), {
    hostName,
  });
  return response.data.id;
};

interface RoomStackResponse {
  nextRoom?: StackRoom;
  flippedRoom?: FlippedRoom;
}

export const skipRoom = async (gameId: string) => {
  const response = await axios.post<RoomStackResponse>(
    buildApiUrl(`/games/${gameId}/roomStack/skip`)
  );

  return response.data.nextRoom;
};

export const flipRoom = async (gameId: string) => {
  const response = await axios.post<RoomStackResponse>(
    buildApiUrl(`/games/${gameId}/roomStack/flip`)
  );

  return response.data.flippedRoom;
};

export const rotateFlipped = async (gameId: string) => {
  const response = await axios.post<RoomStackResponse>(
    buildApiUrl(`/games/${gameId}/roomStack/rotate`)
  );
  return response.data.flippedRoom;
};

export interface PlaceRoomResponse {
  rooms: Room[];
  nextRoom?: StackRoom;
}
export const placeRoom: (
  gameId: string,
  loc: GridLoc
) => Promise<PlaceRoomResponse> = async (gameId, loc) => {
  const response = await axios.post<PlaceRoomResponse>(
    buildApiUrl(`/games/${gameId}/roomStack/place`),
    loc
  );
  return response.data;
};

export const getPlayers: (gameId: string) => Promise<Player[]> = async (
  gameId
) => {
  const response = await axios.get<Player[]>(
    buildApiUrl(`/games/${gameId}/players`)
  );
  return response.data;
};

export const movePlayer: (
  gameId: string,
  playerId: number,
  loc: GridLoc
) => Promise<Player[]> = async (gameId, playerId, loc) => {
  await axios.post(
    buildApiUrl(`/games/${gameId}/players/${playerId}/move`),
    loc
  );
  return getPlayers(gameId);
};

export const setTrait: (
  gameId: string,
  playerId: number,
  trait: TraitName,
  index: number
) => Promise<Player> = async (gameId, playerId, trait, index) => {
  const response = await axios.post(
    buildApiUrl(`/games/${gameId}/players/${playerId}/setTrait`),
    { trait, index }
  );

  return response.data;
};

export const drawEvent: (gameId: string) => Promise<EventCard> = async (
  gameId
) => {
  const response = await axios.post<EventCard>(
    buildApiUrl(`/games/${gameId}/cards/events/draw`)
  );
  return response.data;
};

export const drawItem: (gameId: string) => Promise<ItemCard> = async (
  gameId
) => {
  const response = await axios.post<ItemCard>(
    buildApiUrl(`/games/${gameId}/cards/items/draw`)
  );
  return response.data;
};

export const drawOmen: (gameId: string) => Promise<OmenCard> = async (
  gameId
) => {
  const response = await axios.post<OmenCard>(
    buildApiUrl(`/games/${gameId}/cards/omens/draw`)
  );
  return response.data;
};

export const discardDrawnCard: (gameId: string) => Promise<void> = async (
  gameId
) => {
  await axios.post<void>(buildApiUrl(`/games/${gameId}/cards/drawn/discard`));
};

export const giveDrawnCardToPlayer: (
  gameId: string,
  playerId: number
) => Promise<Player> = async (gameId, playerId) => {
  const response = await axios.post<Player>(
    buildApiUrl(`/games/${gameId}/cards/drawn/giveToPlayer`),
    { playerId }
  );
  return response.data;
};

export const discardHeldCard: (
  gameId: string,
  playerId: number,
  cardId: number
) => Promise<Player> = async (gameId, playerId, cardId) => {
  const response = await axios.post<Player>(
    buildApiUrl(`/games/${gameId}/players/${playerId}/cards/${cardId}/discard`)
  );
  return response.data;
};

export const giveHeldCardToPlayer: (
  gameId: string,
  fromPlayerId: number,
  cardId: number,
  toPlayerId: number
) => Promise<Player[]> = async (gameId, fromPlayerId, cardId, toPlayerId) => {
  const response = await axios.post<Player[]>(
    buildApiUrl(
      `/games/${gameId}/players/${fromPlayerId}/cards/${cardId}/giveToPlayer`
    ),
    { toPlayerId }
  );

  return response.data;
};

export const rollDice: (
  gameId: string,
  numDice: number,
  type: DiceRollType
) => Promise<DiceRoll> = async (gameId, numDice, type) => {
  const response = await axios.post<DiceRoll>(
    buildApiUrl(`/games/${gameId}/dice`),
    { numDice, type }
  );

  return response.data;
};

export const addMonster: (gameId: string) => Promise<Monster> = async (
  gameId
) => {
  const response = await axios.post<Monster>(
    buildApiUrl(`/games/${gameId}/monsters`)
  );

  return response.data;
};

export const moveMonster: (
  gameId: string,
  monsterId: number,
  loc: GridLoc
) => Promise<Monster> = async (gameId, monsterId, loc) => {
  const response = await axios.post<Monster>(
    buildApiUrl(`/games/${gameId}/monsters/${monsterId}/move`),
    loc
  );

  return response.data;
};

export const moveRoom: (
  gameId: string,
  roomId: number,
  loc: GridLoc
) => Promise<void> = async (gameId, roomId, loc) => {
  await axios.post<void>(
    buildApiUrl(`/games/${gameId}/rooms/${roomId}/move`),
    loc
  );
};

export const rotateRoom: (
  gameId: string,
  roomId: number
) => Promise<void> = async (gameId, roomId) => {
  await axios.post<void>(
    buildApiUrl(`/games/${gameId}/rooms/${roomId}/rotate`)
  );
};

export const returnRoomToStack: (
  gameId: string,
  roomId: number
) => Promise<void> = async (gameId, roomId) => {
  await axios.post<void>(
    buildApiUrl(`/games/${gameId}/rooms/${roomId}/return`)
  );
};
