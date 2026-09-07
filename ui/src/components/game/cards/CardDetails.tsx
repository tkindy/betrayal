import { FunctionComponent, ReactElement, ReactNode } from 'react';
import {
  RollTarget,
  RollTableRow,
  RollTable,
  Card,
  EventCard as EventCardModel,
  ItemCard as ItemCardModel,
  OmenCard as OmenCardModel,
} from '../../../features/models';
import './CardDetails.css';

interface BaseCardDetailsProps {
  color: string;
  renderControls: () => ReactElement<any, any>[];
  children: ReactNode;
}

const BaseCardDetails: FunctionComponent<BaseCardDetailsProps> = ({
  color,
  children,
  renderControls,
}) => (
  <div className="cardDetailsWrapper">
    <div
      className="cardDetails"
      style={{
        backgroundColor: color,
      }}
    >
      <div className="cardContentsContainer">{children}</div>
    </div>
    <div className="cardDetailsControls">{renderControls()}</div>
  </div>
);

interface EventCardDetailsProps {
  card: EventCardModel;
  renderControls: () => ReactElement<any, any>[];
}

const renderRollTarget = (target: RollTarget) => {
  switch (target.type) {
    case 'EXACT':
      return `${target.target}`;
    case 'RANGE':
      return `${target.start}-${target.end}`;
    case 'MIN':
      return `${target.minimum}+`;
  }
};

const renderRollTableRow = (row: RollTableRow, index: number) => {
  return (
    <tr key={index} className="rollTableRow">
      <td className="rollTarget">{renderRollTarget(row.target)}</td>
      <td className="rollOutcome">{row.outcome}</td>
    </tr>
  );
};

const renderRollTable = (rollTable: RollTable) => {
  return (
    <table key="rollTable" className="rollTable">
      <tbody>{rollTable.map(renderRollTableRow)}</tbody>
    </table>
  );
};

const renderFlavorText = (card: Card) => (
  <div className="cardFlavorText">
    {card.flavorText?.split('\n').map((line, index) => (
      <p key={index}>{line}</p>
    ))}
  </div>
);

const renderDescription = (card: Card) => (
  <div className="cardDescription">
    {card.description.split('\n').map((line, index) => {
      if (line === '<rollTable>') {
        return renderRollTable(card.rollTable!!);
      }
      return <p key={index}>{line}</p>;
    })}
  </div>
);

const EventCardDetails: FunctionComponent<EventCardDetailsProps> = ({
  card,
  renderControls,
}) => {
  return (
    <BaseCardDetails color="#edd281" renderControls={renderControls}>
      <p className="cardName">{card.name}</p>
      <p className="cardCondition">{card.condition}</p>
      {renderFlavorText(card)}
      {renderDescription(card)}
    </BaseCardDetails>
  );
};

interface ItemCardDetailsProps {
  card: ItemCardModel;
  renderControls: () => ReactElement<any, any>[];
}

const ItemCardDetails: FunctionComponent<ItemCardDetailsProps> = ({
  card,
  renderControls,
}) => {
  return (
    <BaseCardDetails color="#bc7043" renderControls={renderControls}>
      <p className="cardName">{card.name}</p>
      <p className="cardSubtype">{card.subtype}</p>
      {renderFlavorText(card)}
      {renderDescription(card)}
    </BaseCardDetails>
  );
};

interface OmenCardDetailsProps {
  card: OmenCardModel;
  renderControls: () => ReactElement<any, any>[];
}

const OmenCardDetails: FunctionComponent<OmenCardDetailsProps> = ({
  card,
  renderControls,
}) => {
  return (
    <BaseCardDetails color="#a5c96c" renderControls={renderControls}>
      <p className="cardName">{card.name}</p>
      <p className="cardSubtype">{card.subtype}</p>
      {renderFlavorText(card)}
      {renderDescription(card)}
      <p>Make a haunt roll now.</p>
    </BaseCardDetails>
  );
};

interface CardDetailsProps {
  card: Card;
  renderControls: () => ReactElement<any, any>[];
}

const CardDetails: FunctionComponent<CardDetailsProps> = ({
  card,
  renderControls,
}) => {
  switch (card.type) {
    case 'EVENT':
      return <EventCardDetails card={card} renderControls={renderControls} />;
    case 'ITEM':
      return <ItemCardDetails card={card} renderControls={renderControls} />;
    case 'OMEN':
      return <OmenCardDetails card={card} renderControls={renderControls} />;
  }
};

export default CardDetails;
