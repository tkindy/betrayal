import React, { FunctionComponent } from 'react';

interface DiscardControlProps {
  onClick: () => void;
}

const DiscardControl: FunctionComponent<DiscardControlProps> = ({
  onClick,
}) => {
  return (
    <button className="discardButton" onClick={onClick}>
      Discard
    </button>
  );
};

export default DiscardControl;
