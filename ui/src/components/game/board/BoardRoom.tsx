import { FunctionComponent, useState } from 'react';
import { Group, Text } from 'react-konva';
import Room from '../room/Room';
import RoomName from '../room/RoomName';
import { pointToGridLoc, useGridBox, useGridSize } from './grid';
import { Room as RoomModel } from '../../../features/models';
import { BoundingBox, Dimensions, getRoomDetailsBox } from '../../layout';
import RoomFeatures from '../room/RoomFeatures';
import { Point, subtract, translate } from '../../geometry';
import Hovercard, { CardDirection } from '../../Hovercard';
import { useRender } from '../../hooks';
import {
  moveRoom,
  returnRoomToStack,
  rotateRoom,
} from '../../../features/board';
import { useAppDispatch } from '../../../hooks';

const hovercardDimensions: Dimensions = { width: 200, height: 250 };

interface RoomHovercardProps {
  hovered: boolean;
  roomBox: BoundingBox;
  room: RoomModel;
}

const RoomHovercard: FunctionComponent<RoomHovercardProps> = ({
  hovered,
  roomBox,
  room: { name, description },
}) => {
  return (
    <Hovercard
      enabled={hovered}
      targetBox={roomBox}
      contentDimensions={hovercardDimensions}
      direction={CardDirection.RIGHT}
      renderContent={({ topLeft, dimensions: { width, height } }) => {
        const { x, y } = topLeft;
        return (
          <Group x={x} y={y}>
            <Text
              x={0}
              y={0}
              width={width}
              height={height / 5}
              text={name}
              align="center"
              verticalAlign="middle"
              fontSize={20}
              fontStyle="bold"
            />
            <Text
              x={0}
              y={(3 * height) / 10}
              width={width}
              height={(7 * height) / 10}
              text={description}
              align="center"
              verticalAlign="middle"
              fontSize={16}
            />
          </Group>
        );
      }}
    />
  );
};

const BoardRoom: FunctionComponent<RoomModel> = (room) => {
  const { id, name, loc, doorDirections, features, barrier } = room;
  const box = useGridBox(loc);
  const detailsBox = getRoomDetailsBox(box);
  const {
    dimensions: { width: detailsWidth, height: detailsHeight },
  } = detailsBox;
  const detailsTopLeft = subtract(detailsBox.topLeft, box.topLeft);
  const nameBox = {
    topLeft: detailsTopLeft,
    dimensions: { width: detailsWidth, height: detailsHeight / 2 },
  };
  const featuresBox = {
    topLeft: translate(detailsTopLeft, 0, detailsHeight / 2),
    dimensions: { width: detailsWidth, height: detailsHeight / 2 },
  };
  const [hovered, setHovered] = useState(false);
  const dispatch = useAppDispatch();
  const render = useRender();
  const gridSize = useGridSize();

  return (
    <Group>
      <Group
        x={box.topLeft.x}
        y={box.topLeft.y}
        width={box.dimensions.width}
        height={box.dimensions.height}
        onTap={() => setHovered(!hovered)}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        draggable
        onDragStart={() => setHovered(false)}
        onDragEnd={(e) => {
          e.cancelBubble = true; // avoid dragging the board

          const center: Point = {
            x: e.target.position().x + box.dimensions.width / 2,
            y: e.target.position().y + box.dimensions.height / 2,
          };

          dispatch(moveRoom({ id, loc: pointToGridLoc(center, gridSize) }));

          render(); // to snap back if dropped in an invalid spot
        }}
        onDblClick={() => dispatch(rotateRoom({ id }))}
        onDblTap={() => dispatch(rotateRoom({ id }))}
        onContextMenu={(e) => {
          setHovered(false);
          dispatch(returnRoomToStack({ id }));
        }}
      >
        <Room
          box={{ ...box, topLeft: { x: 0, y: 0 } }}
          doorDirections={doorDirections}
        />
        <RoomName box={nameBox} name={name} />
        <RoomFeatures
          box={featuresBox}
          features={features.concat(barrier?.features || [])}
        />
      </Group>
      <RoomHovercard hovered={hovered} roomBox={box} room={room} />
    </Group>
  );
};

export default BoardRoom;
