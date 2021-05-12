import React from 'react';
import { useWindowDimensions } from '../../windowDimensions';

const SearchBar = () => {
  const { width: windowWidth } = useWindowDimensions();
  const width = windowWidth / 4;

  return (
    <input
      style={{
        position: 'absolute',
        top: 10,
        left: (windowWidth - width) / 2,
        width,
        opacity: 0.7,
        textAlign: 'center',
      }}
      placeholder="Find anything"
    />
  );
};

export default SearchBar;
