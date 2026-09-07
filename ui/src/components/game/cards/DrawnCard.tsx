import { FunctionComponent } from 'react';
import './DrawnCard.css';
import CardDetails from './CardDetails';
import DiscardControl from './DiscardControl';
import GiveToPlayerControl from './GiveToPlayerControl';
import {
  discardDrawnCard,
  giveDrawnCardToPlayer,
} from '../../../features/cardStacks';
import { getPlayers } from '../../../features/selectors';
import { useAppDispatch, useAppSelector } from '../../../hooks';

interface DrawnCardProps {}

const DrawnCard: FunctionComponent<DrawnCardProps> = () => {
  const dispatch = useAppDispatch();
  const drawnCard = useAppSelector((state) => state.cardStacks.drawnCard);
  const players = useAppSelector(getPlayers);

  if (!drawnCard) {
    return null;
  }

  return (
    <div className="drawnCardBackground">
      <CardDetails
        card={drawnCard}
        renderControls={() => [
          <DiscardControl
            key="discard"
            onClick={() => dispatch(discardDrawnCard())}
          />,
          <GiveToPlayerControl
            key="giveToPlayer"
            players={players}
            onChange={(playerId) =>
              dispatch(giveDrawnCardToPlayer({ playerId }))
            }
          />,
        ]}
      />
    </div>
  );
};

export default DrawnCard;
