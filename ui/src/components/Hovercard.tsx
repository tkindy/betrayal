import React, { FunctionComponent, ReactElement } from 'react';
import { Group, Line } from 'react-konva';
import OverlayPortal from './game/portal/OverlayPortal';
import { pointsToArray, translate } from './geometry';
import { BoundingBox, Dimensions, getCenter } from './layout';

const MARGIN = 20;
const PADDING = 15;
const TRIANGLE_BASE_WIDTH = 20;
const TRIANGLE_HEIGHT = 15;

export enum CardDirection {
  UP = 'UP',
  DOWN = 'DOWN',
  LEFT = 'LEFT',
  RIGHT = 'RIGHT',
}

const buildContentBox: (
  targetBox: BoundingBox,
  contentDimensions: Dimensions,
  direction: CardDirection
) => BoundingBox = (targetBox, contentDimensions, direction) => {
  const {
    dimensions: { width: targetWidth, height: targetHeight },
  } = targetBox;
  const targetCenter = getCenter(targetBox);
  const { width: contentWidth, height: contentHeight } = contentDimensions;

  let dx, dy;
  switch (direction) {
    case CardDirection.UP:
      dx = -contentWidth / 2;
      dy = -(targetHeight / 2 + MARGIN + PADDING + contentHeight);
      break;
    case CardDirection.DOWN:
      dx = -contentWidth / 2;
      dy = targetHeight / 2 + MARGIN + PADDING;
      break;
    case CardDirection.LEFT:
      dx = -(targetWidth / 2 + MARGIN + PADDING + contentWidth);
      dy = -contentHeight / 2;
      break;
    case CardDirection.RIGHT:
      dx = targetWidth / 2 + MARGIN + PADDING;
      dy = -contentHeight / 2;
      break;
  }

  return {
    topLeft: translate(targetCenter, dx, dy),
    dimensions: contentDimensions,
  };
};

const getCardPoints: (
  contentBox: BoundingBox,
  direction: CardDirection
) => number[] = (
  {
    topLeft: contentTopLeft,
    dimensions: { width: contentWidth, height: contentHeight },
  },
  direction
) => {
  const topLeft = translate(contentTopLeft, -PADDING, -PADDING);
  const width = contentWidth + 2 * PADDING;
  const height = contentHeight + 2 * PADDING;
  const rectPoints = [
    topLeft,
    translate(topLeft, width, 0),
    translate(topLeft, width, height),
    translate(topLeft, 0, height),
  ];

  let triangleIndex, trianglePoints;
  switch (direction) {
    case CardDirection.UP:
      triangleIndex = 3;
      trianglePoints = [
        translate(topLeft, width / 2 + TRIANGLE_BASE_WIDTH / 2, height),
        translate(topLeft, width / 2, height + TRIANGLE_HEIGHT),
        translate(topLeft, width / 2 - TRIANGLE_BASE_WIDTH / 2, height),
      ];
      break;
    case CardDirection.DOWN:
      triangleIndex = 1;
      trianglePoints = [
        translate(topLeft, width / 2 - TRIANGLE_BASE_WIDTH / 2, 0),
        translate(topLeft, width / 2, -TRIANGLE_HEIGHT),
        translate(topLeft, width / 2 + TRIANGLE_BASE_WIDTH / 2, 0),
      ];
      break;
    case CardDirection.LEFT:
      triangleIndex = 2;
      trianglePoints = [
        translate(topLeft, width, height / 2 - TRIANGLE_BASE_WIDTH / 2),
        translate(topLeft, width + TRIANGLE_HEIGHT, height / 2),
        translate(topLeft, width, height / 2 + TRIANGLE_BASE_WIDTH / 2),
      ];
      break;
    case CardDirection.RIGHT:
      triangleIndex = 4;
      trianglePoints = [
        translate(topLeft, 0, height / 2 + TRIANGLE_BASE_WIDTH / 2),
        translate(topLeft, -TRIANGLE_HEIGHT, height / 2),
        translate(topLeft, 0, height / 2 - TRIANGLE_BASE_WIDTH / 2),
      ];
      break;
  }

  return pointsToArray(
    rectPoints
      .slice(0, triangleIndex)
      .concat(trianglePoints)
      .concat(rectPoints.slice(triangleIndex))
  );
};

interface HovercardProps {
  enabled: boolean;
  targetBox: BoundingBox;
  contentDimensions: Dimensions;
  direction: CardDirection;
  renderContent: (box: BoundingBox) => ReactElement<any, any> | null;
}

const Hovercard: FunctionComponent<HovercardProps> = ({
  enabled,
  targetBox,
  contentDimensions,
  direction,
  renderContent,
}) => {
  const contentBox = buildContentBox(targetBox, contentDimensions, direction);

  return (
    <OverlayPortal enabled={enabled}>
      {enabled && (
        <Group>
          <Line
            points={getCardPoints(contentBox, direction)}
            closed={true}
            fill="gray"
            stroke="black"
          />
          {renderContent(contentBox)}
        </Group>
      )}
    </OverlayPortal>
  );
};

export default Hovercard;
