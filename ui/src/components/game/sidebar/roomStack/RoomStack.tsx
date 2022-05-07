import React, { FunctionComponent } from 'react';
import RoomStackControl from './RoomStackControl';
import StackRoom from './StackRoom';

interface RoomStackProps {}

const RoomStack: FunctionComponent<RoomStackProps> = () => {
  return (
    <>
      <StackRoom />
      <RoomStackControl />
    </>
  );
};

export default RoomStack;
