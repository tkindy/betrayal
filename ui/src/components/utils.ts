import { Player } from '../features/models';

export const renderPlayerName = ({ name, characterName }: Player) =>
  `${characterName} (${name})`;
