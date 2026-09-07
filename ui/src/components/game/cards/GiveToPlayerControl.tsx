import React, { FunctionComponent } from 'react';
import { Player } from '../../../features/models';
import { renderPlayerName } from '../../utils';

interface GiveToPlayerControlProps {
  players?: Player[];
  onChange: (playerId: number) => void;
}

const GiveToPlayerControl: FunctionComponent<GiveToPlayerControlProps> = ({
  players,
  onChange,
}) => {
  if (!players) {
    return null;
  }

  return (
    <select onChange={(e) => onChange(parseInt(e.target.value))}>
      <option value="">Give card to...</option>
      {players.map((player) => (
        <option key={player.id} value={player.id}>
          {renderPlayerName(player)}
        </option>
      ))}
    </select>
  );
};

export default GiveToPlayerControl;
