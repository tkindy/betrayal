import React, { FunctionComponent } from 'react';
import { Text } from 'react-konva';
import { BoundingBox } from '../../layout';

interface RoomNameProps {
  box: BoundingBox;
  name: string;
}

const RoomName: FunctionComponent<RoomNameProps> = ({
  box: {
    topLeft: { x, y },
    dimensions: { width, height },
  },
  name,
}) => {
  return (
    <Text
      x={x}
      y={y}
      width={width}
      height={height}
      text={name}
      fontSize={16}
      fill="red"
      align="center"
      verticalAlign="middle"
      ellipsis={true}
    />
  );
};

export default RoomName;
