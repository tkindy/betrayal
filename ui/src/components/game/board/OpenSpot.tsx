import { FunctionComponent, useState } from 'react';
import { Group, Rect } from 'react-konva';
import { openSpotClicked } from '../../../features/board';
import { useAppDispatch, useAppSelector } from '../../../hooks';
import Room, { Direction } from '../room/Room';
import { GridLoc, useGridBox } from './grid';

interface OpenSpotProps {
  loc: GridLoc;
  from: Direction[];
}

const OpenSpot: FunctionComponent<OpenSpotProps> = ({ loc, from }) => {
  const box = useGridBox(loc);
  const {
    topLeft: { x, y },
    dimensions: { width, height },
  } = box;
  const [hovered, setHovered] = useState(false);
  const dispatch = useAppDispatch();
  const onClick = () => dispatch(openSpotClicked(loc, from));
  const flippedRoom = useAppSelector((state) => state.roomStack.flippedRoom);
  const onMouseEnter = () => setHovered(true);
  const onMouseLeave = () => setHovered(false);

  if (hovered && flippedRoom) {
    return (
      <Group
        onClick={onClick}
        onTap={onClick}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
      >
        <Room
          box={box}
          doorDirections={flippedRoom.doorDirections}
          opacity={0.7}
        />
      </Group>
    );
  }

  return (
    <Rect
      x={x}
      y={y}
      width={width}
      height={height}
      onClick={onClick}
      onTap={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    />
  );
};

export default OpenSpot;
