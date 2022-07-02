import { FunctionComponent, useContext, useState } from 'react';
import {
  closeStackContents,
  drawEvent,
  drawItem,
  drawOmen,
} from '../../../features/cardStacks';
import { Card } from '../../../features/models';
import { useAppDispatch, useAppSelector } from '../../../hooks';
import { SendContext } from '../SendContext';
import './DrawControl.css';

const StackContents: FunctionComponent<{ contents: Card[] }> = ({
  contents,
}) => {
  const dispatch = useAppDispatch();
  const [search, setSearch] = useState('');
  const filtered = contents.filter((card) =>
    card.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="stack-contents">
      <button
        onClick={() => {
          dispatch(closeStackContents());
        }}
      >
        Close
      </button>
      <input
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
        }}
      />
      <ul
        style={{
          maxHeight: '100px',
          overflowY: 'scroll',
          backgroundColor: 'white',
          listStyle: 'none',
          padding: '0 10px',
        }}
      >
        {filtered.map((card) => (
          <li key={card.name + card.description} style={{ padding: '2px 0' }}>
            <button>{card.name}</button>
          </li>
        ))}
      </ul>
    </div>
  );
};

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
  const stackContents = useAppSelector(
    (state) => state.cardStacks.stackContents
  );
  if (stackContents) {
    return <StackContents contents={stackContents} />;
  }

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
