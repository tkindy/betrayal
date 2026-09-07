import { FunctionComponent } from 'react';
import { addMonster } from '../../../features/monsters';
import { useAppDispatch } from '../../../hooks';

interface AddMonsterControlProps {}

const AddMonsterControl: FunctionComponent<AddMonsterControlProps> = () => {
  const dispatch = useAppDispatch();

  return <button onClick={() => dispatch(addMonster())}>Add monster</button>;
};

export default AddMonsterControl;
