import React, { FunctionComponent } from 'react';
import { Text } from 'react-konva';
import { Feature } from '../../../features/models';
import { BoundingBox } from '../../layout';

interface RoomFeaturesProps {
  box: BoundingBox;
  features: Feature[];
}

const renderFeature: (f: Feature) => string = (f) => {
  switch (f) {
    case Feature.ITEM:
      return 'I';
    case Feature.EVENT:
      return 'E';
    case Feature.OMEN:
      return 'O';
    case Feature.DUMBWAITER:
      return 'D';
    case Feature.ANY:
      return 'A';
  }
};

const RoomFeatures: FunctionComponent<RoomFeaturesProps> = ({
  box: {
    topLeft: { x, y },
    dimensions: { width, height },
  },
  features,
}) => {
  return (
    <Text
      x={x}
      y={y}
      width={width}
      height={height}
      text={features.map(renderFeature).join(' ')}
      fill="red"
      fontSize={16}
      fontFamily="serif"
      fontStyle="bold"
      align="center"
      verticalAlign="top"
    />
  );
};

export default RoomFeatures;
