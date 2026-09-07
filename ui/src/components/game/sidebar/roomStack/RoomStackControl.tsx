import { CSSProperties, FunctionComponent } from 'react';
import {
  flipRoomStack,
  rotateFlipped,
  skipRoom,
} from '../../../../features/roomStack';
import { useAppDispatch, useAppSelector } from '../../../../hooks';

const StackButtons: FunctionComponent<{}> = () => {
  const dispatch = useAppDispatch();
  const buttonStyle: CSSProperties = {
    flex: '2',
    height: '100%',
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        height: '100%',
      }}
    >
      <button onClick={() => dispatch(flipRoomStack())} style={buttonStyle}>
        Use
      </button>
      <div style={{ flex: '1' }}></div>
      <button onClick={() => dispatch(skipRoom())} style={buttonStyle}>
        Next
      </button>
    </div>
  );
};

const FlippedRoomButtons: FunctionComponent<{}> = () => {
  const dispatch = useAppDispatch();

  return (
    <div style={{ height: '100%' }}>
      <button
        onClick={() => dispatch(rotateFlipped())}
        style={{ height: '100%', width: '100%' }}
      >
        Rotate
      </button>
    </div>
  );
};

interface RoomStackControlProps {}

const RoomStackControl: FunctionComponent<RoomStackControlProps> = () => {
  const flippedRoom = useAppSelector((state) => state.roomStack.flippedRoom);

  return (
    <div className="room-stack-control">
      {flippedRoom ? <FlippedRoomButtons /> : <StackButtons />}
    </div>
  );
};

export default RoomStackControl;
