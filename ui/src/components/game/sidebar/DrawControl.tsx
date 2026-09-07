import { FunctionComponent } from 'react';
import { drawEvent, drawItem, drawOmen } from '../../../features/cardStacks';
import { useAppDispatch } from '../../../hooks';
import './DrawControl.css';

interface DrawButtonProps {
  entity: string;
  thunk: () => any;
}

const DrawButton: FunctionComponent<DrawButtonProps> = ({ entity, thunk }) => {
  const dispatch = useAppDispatch();
  return (
    <button
      className="drawCardButton"
      onClick={() => dispatch(thunk())}
    >{`Draw ${entity}`}</button>
  );
};

const buttonProps: DrawButtonProps[] = [
  { entity: 'event', thunk: drawEvent },
  { entity: 'item', thunk: drawItem },
  { entity: 'omen', thunk: drawOmen },
];

interface DrawControlProps {}

const DrawControl: FunctionComponent<DrawControlProps> = () => {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-around',
      }}
    >
      {buttonProps.map((props) => (
        <DrawButton key={props.entity} {...props} />
      ))}
    </div>
  );
};

export default DrawControl;
