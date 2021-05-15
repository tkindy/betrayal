import { FC, useRef, useState } from 'react';
import { SearchResult } from '../../../features/models';

const dummyItems: SearchResult[] = [
  { name: 'foo', type: 'ITEM' },
  { name: 'bar', type: 'EVENT' },
  { name: 'baz', type: 'ROOM' },
];

interface SearchResultsProps {
  width: number;
  term: string;
  onClickResult: () => void;
  onMouseEnterResult: () => void;
  onMouseLeaveResult: () => void;
}

const SearchResults: FC<SearchResultsProps> = ({
  width,
  term,
  onClickResult,
  onMouseEnterResult,
  onMouseLeaveResult,
}) => {
  const filtered = dummyItems.filter((item) => item.name.startsWith(term));

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
        filtered.map((item) => (
          <div key={item.name}>
            <button
              onClick={() => {
                console.log('click');
                onClickResult();
              }}
              onMouseEnter={onMouseEnterResult}
              onMouseLeave={onMouseLeaveResult}
            >
              {item.name} - {item.type}
            </button>
          </div>
        ))
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
  const [inputFocused, setInputFocused] = useState(false);
  const [onResult, setOnResult] = useState(false);

  return (
    <div
      className="find-in-stacks-container"
      onFocus={() => {
        setInputFocused(true);
      }}
      onBlur={() => {
        setInputFocused(false);
      }}
    >
      <input
        style={{
          textAlign: 'center',
        }}
        placeholder="Find in stacks"
        ref={inputRef}
        onChange={(e) => setTerm(e.target.value)}
        value={term}
      />
      {(inputFocused || onResult) && term && (
        <SearchResults
          width={inputRef.current?.clientWidth || 0}
          term={term}
          onClickResult={() => {
            setTerm('');
          }}
          onMouseEnterResult={() => setOnResult(true)}
          onMouseLeaveResult={() => setOnResult(false)}
        />
      )}
    </div>
  );
};

export default FindInStacks;
