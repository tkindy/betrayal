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

const SearchStackButton: FunctionComponent<{}> = () => {
  return <button style={{ margin: '5px 2px' }}>🔍</button>;
};

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
        <div
          key={props.entity}
          style={{
            flex: '0 1 50px',
            display: 'grid',
            gridTemplateColumns: '75% 25%',
          }}
        >
          <DrawButton {...props} />
          <SearchStackButton />
        </div>
      ))}
    </div>
  );
};

export default DrawControl;
