import './Home.css';
import { FC, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createLobby } from '../api/api';
import { NameInput } from './NameInput';

const Home: FC<{}> = () => {
  const navigate = useNavigate();
  const [hostName, setHostName] = useState('');

  return (
    <div>
      <h1>Betrayal at House on the Hill</h1>
      <div className="container">
        <NameInput
          className="host-name"
          form="new-game-form"
          placeholder="Your name"
          required
          onChange={(e) => {
            setHostName(e.target.value);
          }}
        />
        <button className="new-game" form="new-game-form">
          New game
        </button>
        <form
          id="new-game-form"
          onSubmit={async (e) => {
            e.preventDefault();

            const lobbyId = await createLobby(hostName);
            navigate(`/lobby/${lobbyId}?name=${encodeURIComponent(hostName)}`);
          }}
        />
      </div>
    </div>
  );
};

export default Home;
