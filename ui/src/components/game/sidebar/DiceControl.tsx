import { CSSProperties, FunctionComponent, useState } from 'react';
import { rollDice } from '../../../features/diceRolls';
import './DiceControl.css';
import { useAppDispatch, useAppSelector } from '../../../hooks';
import { getNumHeldOmens } from '../../../features/selectors';

const HauntRollButton: FunctionComponent<{}> = () => {
  const numOmens = useAppSelector(getNumHeldOmens);
  const dispatch = useAppDispatch();

  return (
    <button
      style={{ width: '100%', margin: '3px', padding: '3px' }}
      onClick={() => {
        dispatch(rollDice({ numDice: 6, type: 'HAUNT' }));
      }}
    >
      Haunt roll ({numOmens} omen{numOmens === 1 ? '' : 's'})
    </button>
  );
};

interface DieProps {
  value: number;
}

const Die: FunctionComponent<DieProps> = ({ value }) => {
  let name;
  switch (value) {
    case 0:
      name = 'zero';
      break;
    case 1:
      name = 'one';
      break;
    case 2:
      name = 'two';
      break;
  }

  return (
    <img src={`/images/dice/${name}.svg`} alt={name} className="die-value" />
  );
};

interface DiceRowProps {
  values: number[];
}

const DiceRow: FunctionComponent<DiceRowProps> = ({ values }) => {
  return (
    <div className="dice-row">
      {values.map((value, index) => (
        <Die key={index} value={value} />
      ))}
    </div>
  );
};

interface DiceProps {
  values?: number[];
}

const Dice: FunctionComponent<DiceProps> = ({ values = [] }) => {
  return (
    <div>
      <DiceRow key={0} values={values.slice(0, 4)} />
      <DiceRow key={1} values={values.slice(4, 8)} />
    </div>
  );
};

const HauntTimeMessage: FunctionComponent<{}> = () => {
  const { roll, couldTriggerHaunt } = useAppSelector(
    (state) => state.diceRolls
  );
  const numOmens = useAppSelector(getNumHeldOmens);

  let message: string, style: CSSProperties;
  if (!couldTriggerHaunt) {
    message = '';
    style = {};
  } else if (roll!.values.reduce((a, b) => a + b, 0) < numOmens) {
    message = 'Haunt time!';
    style = { color: '#ac1212' };
  } else {
    message = 'No haunt';
    style = {};
  }

  return (
    <p
      style={{
        ...style,
        height: '20px',
        fontWeight: 'bold',
        textAlign: 'center',
      }}
    >
      {message}
    </p>
  );
};

const DiceControl: FunctionComponent<{}> = () => {
  const { roll } = useAppSelector((state) => state.diceRolls);
  const dispatch = useAppDispatch();
  const [numDice, setNumDice] = useState<number>(8);

  return (
    <div className="dice-background">
      <div className="dice-controls">
        <input
          className="dice-count"
          type="number"
          min="1"
          max="8"
          value={numDice}
          onChange={(e) => setNumDice(parseInt(e.target.value))}
        />
        <button
          className="roll-dice-button"
          onClick={() => dispatch(rollDice({ numDice, type: 'AD_HOC' }))}
        >
          Roll
        </button>
      </div>

      <HauntRollButton />
      <Dice values={roll?.values} />
      <HauntTimeMessage />
    </div>
  );
};

export default DiceControl;
