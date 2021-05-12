import { FC, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import { searchStacks } from '../../../features/search';
import { useAppDispatch } from '../../../hooks';
import { RootState } from '../../../store';

const dummyItems = ['foo', 'bar', 'baz'];

interface SearchResultsProps {
  width: number;
  term: string;
}

const SearchResults: FC<SearchResultsProps> = ({ width }) => {
  return (
    <div
      style={{
        position: 'absolute',
        backgroundColor: 'lightgrey',
        width: width - 10,
        padding: 5,
        borderRadius: 5,
        border: '1px solid black',
      }}
    >
      {dummyItems.map((item) => (
        <p key={item}>{item}</p>
      ))}
    </div>
  );
};

const FindInStacks: FC<{}> = () => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [expanded, setExpanded] = useState(false);
  const term = inputRef.current?.value || '';

  return (
    <div className="find-in-stacks-container">
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
      {expanded && (
        <SearchResults width={inputRef.current?.clientWidth || 0} term={term} />
      )}
    </div>
  );
};

export default FindInStacks;
