import { FunctionComponent, useState } from 'react';
import { Circle, Group, Rect, Text } from 'react-konva';
import { partition } from '../../../utils';
import { translate } from '../../geometry';
import {
  GridLoc,
  pointToGridLoc,
  useGridBox,
  useGridSize,
} from '../board/grid';
import {
  Player as PlayerModel,
  Agent as AgentModel,
  Monster as MonsterModel,
} from '../../../features/models';
import { playerDropped, switchSelectedPlayer } from '../../../features/players';
import { BoundingBox, getCenter, getPlayersBox } from '../../layout';
import { useRender } from '../../hooks';
import PlayerHovercard from './PlayerHovercard';
import { monsterDropped } from '../../../features/monsters';
import { useAppDispatch } from '../../../hooks';

interface MonsterProps {
  box: BoundingBox;
  monster: MonsterModel;
}

const Monster: FunctionComponent<MonsterProps> = ({
  box,
  monster: { id, number },
}) => {
  const { width, height } = box.dimensions;
  const side = Math.min(width, height);
  const center = getCenter(box);
  const { x, y } = translate(center, side / -2, side / -2);

  const dispatch = useAppDispatch();
  const render = useRender();
  const gridSize = useGridSize();

  return (
    <Group
      x={x}
      y={y}
      draggable
      onDragEnd={(e) => {
        e.cancelBubble = true; // avoid dragging the board

        dispatch(
          monsterDropped(id, pointToGridLoc(e.target.position(), gridSize))
        );

        render(); // to snap back if dropped in an invalid spot
      }}
    >
      <Rect
        x={0}
        y={0}
        width={width}
        height={height}
        fill="orange"
        stroke="white"
      />
      <Text
        x={0}
        y={0}
        width={width}
        height={height}
        text={number.toString()}
        align="center"
        verticalAlign="middle"
        fontStyle="bold"
      />
    </Group>
  );
};

interface PlayerProps {
  box: BoundingBox;
  player: PlayerModel;
}

const Player: FunctionComponent<PlayerProps> = ({ box, player }) => {
  const { x, y } = getCenter(box);
  const { width, height } = box.dimensions;
  const radius = Math.min(width, height) / 2;
  const { id, color } = player;

  const render = useRender();
  const dispatch = useAppDispatch();
  const gridSize = useGridSize();

  const [hovered, setHovered] = useState(false);

  return (
    <Group>
      <Circle
        x={x}
        y={y}
        radius={radius}
        fill={color}
        stroke="white"
        onDblClick={() => dispatch(switchSelectedPlayer(player.id))}
        draggable
        onDragStart={() => setHovered(false)}
        onDragEnd={(e) => {
          e.cancelBubble = true; // avoid dragging the board

          dispatch(
            playerDropped(id, pointToGridLoc(e.target.position(), gridSize))
          );

          render(); // to snap back if dropped in an invalid spot
        }}
        onTap={() => setHovered(!hovered)}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      />
      <PlayerHovercard hovered={hovered} playerBox={box} player={player} />
    </Group>
  );
};

interface AgentProps {
  box: BoundingBox;
  agent: AgentModel;
}

const Agent: FunctionComponent<AgentProps> = ({ box, agent }) => {
  switch (agent.type) {
    case 'player':
      return <Player box={box} player={agent} />;
    case 'monster':
      return <Monster box={box} monster={agent} />;
  }
};

interface AgentsRowProps {
  box: BoundingBox;
  agents: AgentModel[];
}

const AgentsRow: FunctionComponent<AgentsRowProps> = ({
  agents,
  box: {
    topLeft,
    dimensions: { width, height },
  },
}) => {
  const playerWidth = width / 6;
  const playerHeight = playerWidth;
  const playerSpacing = playerWidth / 2;
  const totalPlayersWidth = agents.length * playerWidth;
  const totalInterPlayerDistance = (agents.length - 1) * playerSpacing;
  const firstTopLeft = translate(
    topLeft,
    (width - (totalPlayersWidth + totalInterPlayerDistance)) / 2,
    (height - playerHeight) / 2
  );
  return (
    <Group>
      {agents.map((agent, i) => (
        <Agent
          key={agent.type + agent.id}
          box={{
            topLeft: translate(
              firstTopLeft,
              i * (playerWidth + playerSpacing),
              0
            ),
            dimensions: { width: playerWidth, height: playerHeight },
          }}
          agent={agent}
        />
      ))}
    </Group>
  );
};

export interface RoomAgentsProps {
  agents: AgentModel[];
  roomLoc: GridLoc;
}

const RoomAgents: FunctionComponent<RoomAgentsProps> = ({
  agents,
  roomLoc,
}) => {
  const roomBox = useGridBox(roomLoc);
  const {
    topLeft,
    dimensions: { width, height },
  } = getPlayersBox(roomBox);

  const byRow = partition(agents, 3);
  const rowHeight = height / byRow.length;

  return (
    <Group>
      {byRow.map((row, i) => (
        <AgentsRow
          key={i}
          box={{
            topLeft: translate(topLeft, 0, i * rowHeight),
            dimensions: { width, height: rowHeight },
          }}
          agents={row}
        />
      ))}
    </Group>
  );
};

export default RoomAgents;
