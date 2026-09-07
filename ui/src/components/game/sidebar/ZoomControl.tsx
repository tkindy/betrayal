import { FunctionComponent } from 'react';
import { zoomIn, zoomOut } from '../../../features/zoom';
import { useAppDispatch } from '../../../hooks';
import './ZoomControl.css';

interface ZoomControlProps {}

const ZoomControl: FunctionComponent<ZoomControlProps> = () => {
  const dispatch = useAppDispatch();

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-around',
        alignItems: 'center',
      }}
    >
      <button className="zoomButton zoomIn" onClick={() => dispatch(zoomIn())}>
        +
      </button>
      <button
        className="zoomButton zoomOut"
        onClick={() => dispatch(zoomOut())}
      >
        -
      </button>
    </div>
  );
};

export default ZoomControl;
