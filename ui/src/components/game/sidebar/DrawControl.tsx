import { FunctionComponent, useContext } from 'react';
import { drawEvent, drawItem, drawOmen } from '../../../features/cardStacks';
import { useAppDispatch } from '../../../hooks';
import { SendContext } from '../SendContext';
import './DrawControl.css';

type CardType = 'EVENT' | 'ITEM' | 'OMEN';

interface DrawButtonProps {
  cardType: CardType;
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
  { cardType: 'EVENT', entity: 'event', thunk: drawEvent },
  { cardType: 'ITEM', entity: 'item', thunk: drawItem },
  { cardType: 'OMEN', entity: 'omen', thunk: drawOmen },
];

const SearchStackButton: FunctionComponent<{
  cardType: CardType;
}> = ({ cardType }) => {
  const send = useContext(SendContext)!;
  return (
    <button
      style={{ margin: '5px 2px' }}
      onClick={() => {
        send({ type: 'search-card-stack', cardType });
      }}
    >
      🔍
    </button>
  );
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
          <SearchStackButton cardType={props.cardType} />
        </div>
      ))}
    </div>
  );
};

export default DrawControl;
