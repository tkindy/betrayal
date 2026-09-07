import React, { FunctionComponent } from 'react';
import ZoomControl from './ZoomControl';
import { BoundingBox, Dimensions } from '../../layout';
import { useWindowDimensions } from '../../windowDimensions';
import RoomStack from './roomStack/RoomStack';
import DrawControl from './DrawControl';
import AddMonsterControl from './AddMonsterControl';
import DiceControl from './DiceControl';

export const SIDEBAR_WIDTH = 215;
export const SIDEBAR_MARGIN = 10;
export const SIDEBAR_PADDING = 10;

const getBox: (windowDimensions: Dimensions) => BoundingBox = ({
  width: windowWidth,
  height: windowHeight,
}) => {
  return {
    topLeft: {
      x: windowWidth - SIDEBAR_MARGIN - SIDEBAR_WIDTH,
      y: SIDEBAR_MARGIN,
    },
    dimensions: {
      width: SIDEBAR_WIDTH,
      height: windowHeight - 2 * SIDEBAR_MARGIN,
    },
  };
};

interface SidebarProps {}

const Sidebar: FunctionComponent<SidebarProps> = () => {
  const {
    topLeft,
    dimensions: { width, height },
  } = getBox(useWindowDimensions());
  const { x, y } = topLeft;

  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y,
        width: width - 2 * SIDEBAR_PADDING,
        height: height - 2 * SIDEBAR_PADDING,
        padding: SIDEBAR_PADDING,
        backgroundColor: 'grey',
        borderRadius: 10,
        border: '2px solid black',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-around',
      }}
    >
      <ZoomControl />
      <DrawControl />
      <DiceControl />
      <AddMonsterControl />
      <RoomStack />
    </div>
  );
};

export default Sidebar;
