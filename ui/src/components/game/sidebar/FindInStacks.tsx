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

const SearchResults: FC<SearchResultsProps> = ({ width, term }) => {
  const filtered = dummyItems.filter((item) => item.startsWith(term));

  return (
    <div
      style={{
        position: 'absolute',
        backgroundColor: 'lightgrey',
        width: width - 10,
        padding: 5,
        borderRadius: 5,
        border: '1px solid black',
        textAlign: 'center',
      }}
    >
      {filtered.length ? (
        filtered.map((item) => <p key={item}>{item}</p>)
      ) : (
        <p>
          <i>No results</i>
        </p>
      )}
    </div>
  );
};

const FindInStacks: FC<{}> = () => {
  const [term, setTerm] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const [expanded, setExpanded] = useState(false);

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
        onChange={(e) => setTerm(e.target.value)}
        value={term}
      />
      {expanded && term && (
        <SearchResults width={inputRef.current?.clientWidth || 0} term={term} />
      )}
    </div>
  );
};

export default FindInStacks;
