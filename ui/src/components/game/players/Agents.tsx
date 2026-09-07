import { FunctionComponent } from 'react';
import { Group } from 'react-konva';
import { getAgentMap } from '../../../features/selectors';
import { useAppSelector } from '../../../hooks';
import { mapCartMap } from '../../../map';
import { toString } from '../board/grid';
import RoomAgents from './RoomAgents';

const Agents: FunctionComponent<{}> = () => {
  const agentMap = useAppSelector(getAgentMap);
  if (!agentMap) {
    return null;
  }

  return (
    <Group>
      {mapCartMap(agentMap, ({ x, y }, agents) => {
        const loc = { gridX: x, gridY: y };
        return <RoomAgents key={toString(loc)} agents={agents} roomLoc={loc} />;
      })}
    </Group>
  );
};

export default Agents;
