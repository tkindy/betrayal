import React, { FunctionComponent } from 'react';
import Hovercard, { CardDirection } from '../../Hovercard';
import { BoundingBox, Dimensions } from '../../layout';
import { Group, Text } from 'react-konva';
import { Player } from '../../../features/models';
import { translate } from '../../geometry';

const DIMENSIONS: Dimensions = { width: 200, height: 100 };

interface TraitProps {
  box: BoundingBox;
  name: string;
  value: number;
}

const Trait: FunctionComponent<TraitProps> = ({
  box: {
    topLeft: { x, y },
    dimensions: { width, height },
  },
  name,
  value,
}) => {
  return (
    <Group>
      <Text
        x={x}
        y={y}
        width={width}
        height={height / 2}
        text={name}
        fontSize={12}
        textDecoration="underline"
        align="center"
        verticalAlign="bottom"
        padding={10}
      />
      <Text
        x={x}
        y={y + height / 2}
        width={width}
        height={height / 2}
        text={`${value}`}
        align="center"
        fontSize={24}
      />
    </Group>
  );
};

interface PlayerHovercardProps {
  hovered: boolean;
  playerBox: BoundingBox;
  player: Player;
}

const PlayerHovercard: FunctionComponent<PlayerHovercardProps> = ({
  hovered,
  playerBox,
  player: { name, characterName, speed, might, sanity, knowledge },
}) => {
  return (
    <Hovercard
      enabled={hovered}
      targetBox={playerBox}
      contentDimensions={DIMENSIONS}
      direction={CardDirection.RIGHT}
      renderContent={({ topLeft, dimensions: { width, height } }) => {
        const { x, y } = topLeft;
        const traitWidth = width / 4;
        const traitHeight = height / 2;
        const traitDimensions = { width: traitWidth, height: traitHeight };

        return (
          <Group>
            <Text
              x={x}
              y={y}
              width={width}
              height={height / 4}
              text={characterName}
              fontSize={20}
              fontStyle="bold"
              align="center"
            />
            <Text
              x={x}
              y={y + height / 4}
              width={width}
              height={height / 4}
              text={name}
              fontSize={16}
              fontStyle="italic"
              align="center"
            />
            {[
              { name: 'SPD', value: speed.value },
              { name: 'MGT', value: might.value },
              { name: 'SAN', value: sanity.value },
              { name: 'KNO', value: knowledge.value },
            ].map(({ name, value }, i) => (
              <Trait
                key={name}
                box={{
                  topLeft: translate(topLeft, i * traitWidth, height / 2),
                  dimensions: traitDimensions,
                }}
                name={name}
                value={value}
              />
            ))}
          </Group>
        );
      }}
    />
  );
};

export default PlayerHovercard;
