import { FunctionComponent, useEffect, useState } from 'react';
import {
  HeldCard,
  Player,
  Trait as TraitModel,
  TraitName,
} from '../../../features/models';
import {
  discardHeldCard,
  giveHeldCardToPlayer,
  setTrait,
  switchSelectedPlayer,
} from '../../../features/players';
import {
  getPlayers,
  getSelectedPlayer,
  getSelectedPlayerId,
} from '../../../features/selectors';
import { useAppDispatch, useAppSelector } from '../../../hooks';
import { BoundingBox, Dimensions } from '../../layout';
import { renderPlayerName } from '../../utils';
import { useWindowDimensions } from '../../windowDimensions';
import CardDetails from '../cards/CardDetails';
import DiscardControl from '../cards/DiscardControl';
import GiveToPlayerControl from '../cards/GiveToPlayerControl';
import {
  SIDEBAR_MARGIN,
  SIDEBAR_PADDING,
  SIDEBAR_WIDTH,
} from '../sidebar/Sidebar';
import './CharacterBar.css';

const CHARACTER_BAR_HEIGHT = SIDEBAR_WIDTH;
const CHARACTER_BAR_MARGIN = SIDEBAR_MARGIN;
const CHARACTER_BAR_PADDING = SIDEBAR_PADDING;

const PlayerSelect: FunctionComponent<{}> = () => {
  const dispatch = useAppDispatch();
  const players = useAppSelector(getPlayers);
  const selectedPlayerId = useAppSelector(getSelectedPlayerId);

  return (
    <select
      onChange={(e) => dispatch(switchSelectedPlayer(parseInt(e.target.value)))}
      value={selectedPlayerId}
      style={{ display: 'block', height: '15%' }}
    >
      {players?.map((player) => (
        <option key={player.id} value={player.id}>
          {renderPlayerName(player)}
        </option>
      ))}
    </select>
  );
};

interface CardHovercardProps {
  card?: HeldCard;
  close: () => void;
}

const CardHovercard: FunctionComponent<CardHovercardProps> = ({
  card,
  close,
}) => {
  const dispatch = useAppDispatch();
  const selectedPlayerId = useAppSelector(getSelectedPlayerId);
  const possiblePlayersToGiveTo = useAppSelector(getPlayers)?.filter(
    (player) => player.id !== selectedPlayerId
  );

  if (!card) {
    return null;
  }

  return (
    <div
      style={{
        position: 'absolute',
        bottom: 200,
        left: 0,
        width: '100%',
        display: 'grid',
      }}
    >
      <CardDetails
        card={card.card}
        renderControls={() => [
          <button key="close" onClick={close}>
            Close
          </button>,
          <DiscardControl
            key="discard"
            onClick={() => {
              close();
              dispatch(discardHeldCard({ cardId: card.id }));
            }}
          />,
          <GiveToPlayerControl
            key="giveToPlayer"
            players={possiblePlayersToGiveTo}
            onChange={(toPlayerId) => {
              close();
              dispatch(giveHeldCardToPlayer({ cardId: card.id, toPlayerId }));
            }}
          />,
        ]}
      />
    </div>
  );
};

interface PlayerInventoryProps {}

const PlayerInventory: FunctionComponent<PlayerInventoryProps> = () => {
  const cards = useAppSelector(getSelectedPlayer)?.cards;
  const [focusedCardId, setFocusedCardId] = useState<number | null>(null);

  useEffect(() => setFocusedCardId(null), [cards]);

  if (!cards || cards.length === 0) {
    return <i>Inventory empty</i>;
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <div className="inventoryContents">
        {cards.map((card) => {
          const className = 'inventoryCard ' + card.card.type;

          return (
            <div
              className={className}
              key={card.id}
              onClick={() => {
                if (focusedCardId === card.id) {
                  setFocusedCardId(null);
                } else {
                  setFocusedCardId(card.id);
                }
              }}
              style={{
                height: CHARACTER_BAR_HEIGHT - 2 * CHARACTER_BAR_PADDING - 50,
              }}
            >
              <div>{card.card.name}</div>
            </div>
          );
        })}
      </div>
      {focusedCardId && (
        <CardHovercard
          card={cards.find((card) => card.id === focusedCardId)}
          close={() => setFocusedCardId(null)}
        />
      )}
    </div>
  );
};

interface TraitProps {
  name: TraitName;
  trait: TraitModel;
  column: number;
  row: number;
}

const Trait: FunctionComponent<TraitProps> = ({ name, trait, column, row }) => {
  const dispatch = useAppDispatch();

  return (
    <div style={{ gridRow: row, gridColumn: column, padding: '5px' }}>
      <div style={{ textAlign: 'center' }}>{name.toUpperCase()}</div>
      {trait.scale.map((value, index) => {
        const starting = index === trait.startingIndex;
        const active = index === trait.index;
        const className = [
          'traitValue',
          starting && 'starting',
          active && 'active',
        ]
          .filter((c) => c)
          .join(' ');

        return (
          <button
            key={index}
            className={className}
            onClick={() => dispatch(setTrait({ trait: name, index }))}
          >
            {value}
          </button>
        );
      })}
    </div>
  );
};

const traitLayout: {
  name: TraitName;
  select: (player: Player) => TraitModel;
  column: number;
  row: number;
}[] = [
  { name: 'SPEED', select: (p) => p.speed, column: 1, row: 1 },
  { name: 'MIGHT', select: (p) => p.might, column: 2, row: 1 },
  { name: 'SANITY', select: (p) => p.sanity, column: 1, row: 2 },
  { name: 'KNOWLEDGE', select: (p) => p.knowledge, column: 2, row: 2 },
];

interface TraitsProps {}

const Traits: FunctionComponent<TraitsProps> = () => {
  const player = useAppSelector(getSelectedPlayer);
  if (!player) {
    return null;
  }

  return (
    <div
      className="traits-container"
      style={{
        display: 'grid',
        gridTemplateRows: '1 1',
        gridTemplateColumns: '1 1',
        flex: '0 0 content',
      }}
    >
      {traitLayout.map(({ name, select, column, row }) => (
        <Trait
          key={name}
          name={name}
          trait={select(player)}
          column={column}
          row={row}
        />
      ))}
    </div>
  );
};

const getBox: (windowDimensions: Dimensions) => BoundingBox = ({
  width: windowWidth,
  height: windowHeight,
}) => {
  return {
    topLeft: {
      x: CHARACTER_BAR_MARGIN,
      y: windowHeight - CHARACTER_BAR_MARGIN - CHARACTER_BAR_HEIGHT,
    },
    dimensions: {
      width:
        windowWidth - 2 * CHARACTER_BAR_MARGIN - SIDEBAR_WIDTH - SIDEBAR_MARGIN,
      height: CHARACTER_BAR_HEIGHT,
    },
  };
};

interface CharacterBarProps {}

const CharacterBar: FunctionComponent<CharacterBarProps> = () => {
  const box = getBox(useWindowDimensions());
  const {
    topLeft: { x, y },
    dimensions: { width, height },
  } = box;

  return (
    <div
      className="characterBarWrapper"
      style={{
        position: 'absolute',
        top: y,
        left: x,
        width: width - 2 * CHARACTER_BAR_PADDING,
        height: height - 2 * CHARACTER_BAR_PADDING,
        backgroundColor: 'grey',
        border: '2px solid black',
        borderRadius: 10,
        padding: CHARACTER_BAR_PADDING,
      }}
    >
      <PlayerSelect />
      <div
        className="character-bar-second-row"
        style={{
          display: 'flex',
          overflowX: 'hidden',
          height: '85%',
        }}
      >
        <Traits />
        <PlayerInventory />
      </div>
    </div>
  );
};

export default CharacterBar;
