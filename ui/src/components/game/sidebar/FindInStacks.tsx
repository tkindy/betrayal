import { FC, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import { searchStacks } from '../../../features/search';
import { useAppDispatch } from '../../../hooks';
import { RootState } from '../../../store';

interface SearchResultsProps {
  width: number;
  onClickResult: () => void;
  onMouseEnterResult: () => void;
  onMouseLeaveResult: () => void;
}

const SearchResults: FC<SearchResultsProps> = ({
  width,
  onClickResult,
  onMouseEnterResult,
  onMouseLeaveResult,
}) => {
  const results = useSelector((state: RootState) => state.search.results);
  const hasResults = !!results?.length;

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
      {hasResults ? (
        results!!.map((item) => (
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
  const dispatch = useAppDispatch();

  const updateTerm = (term: string) => {
    setTerm(term);
    dispatch(searchStacks({ term }));
  };

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
        onChange={(e) => updateTerm(e.target.value)}
        value={term}
      />
      {(inputFocused || onResult) && term && (
        <SearchResults
          width={inputRef.current?.clientWidth || 0}
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
