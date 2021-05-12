import { FC, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import { searchStacks } from '../../../features/search';
import { useAppDispatch } from '../../../hooks';
import { RootState } from '../../../store';

const FindInStacks: FC<{}> = () => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [expanded, setExpanded] = useState(false);

  return (
    <input
      style={{
        textAlign: 'center',
      }}
      placeholder="Find in stacks"
      ref={inputRef}
      onFocus={() => {
        setExpanded(true);
      }}
      onBlur={() => {
        setExpanded(false);
      }}
    />
  );
};

export default FindInStacks;
