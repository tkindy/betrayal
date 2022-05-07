import React, { FunctionComponent } from 'react';
import RoomStackControl from './RoomStackControl';
import StackRoom from './StackRoom';

interface RoomStackProps {}

const RoomStack: FunctionComponent<RoomStackProps> = () => {
  return (
    <div
      style={{
        gridArea: 'room-stack',
        alignSelf: 'end',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <StackRoom />
      <RoomStackControl />
    </div>
  );
};

export default RoomStack;
