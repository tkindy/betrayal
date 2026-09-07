import React, { FunctionComponent } from 'react';
import { Group } from 'react-konva';
import { FlippedRoom } from '../../../../features/models';
import { translate } from '../../../geometry';
import { BoundingBox, getDoorDimensions } from '../../../layout';
import Room from '../../room/Room';
import RoomFeatures from '../../room/RoomFeatures';
import RoomName from '../../room/RoomName';

interface FlippedStackRoomProps {
  box: BoundingBox;
  flippedRoom: FlippedRoom;
}

const FlippedStackRoom: FunctionComponent<FlippedStackRoomProps> = ({
  box,
  flippedRoom: { name, doorDirections, features, barrier },
}) => {
  const { height: doorHeight } = getDoorDimensions(box.dimensions);
  const {
    topLeft: roomTopLeft,
    dimensions: { width: roomWidth, height: roomHeight },
  } = box;
  const innerTopLeft = translate(roomTopLeft, doorHeight, doorHeight);
  const innerWidth = roomWidth - 2 * doorHeight;
  const innerHeight = roomHeight - 2 * doorHeight;
  const nameBox = {
    topLeft: translate(innerTopLeft, 0, innerHeight / 6),
    dimensions: { width: innerWidth, height: innerHeight / 3 },
  };
  const featuresBox = {
    topLeft: translate(innerTopLeft, 0, innerHeight / 2),
    dimensions: { width: innerWidth, height: innerHeight / 6 },
  };

  return (
    <Group>
      <Room box={box} doorDirections={doorDirections} />
      <RoomName box={nameBox} name={name} />
      <RoomFeatures
        box={featuresBox}
        features={features.concat(barrier?.features || [])}
      />
    </Group>
  );
};

export default FlippedStackRoom;
