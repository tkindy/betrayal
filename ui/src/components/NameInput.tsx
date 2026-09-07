import React, { FC } from 'react';

export const NameInput: FC<React.InputHTMLAttributes<HTMLInputElement>> = (
  props
) => {
  return <input type="text" minLength={1} maxLength={12} {...props} />;
};
