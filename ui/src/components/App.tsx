import { Route, Routes } from 'react-router';
import Game from './game/Game';
import Home from './Home';
import Lobby from './lobby/Lobby';

const App = () => {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/lobby/:lobbyId" element={<Lobby />} />
      <Route path="/game/:gameId" element={<Game />} />
    </Routes>
  );
};

export default App;
