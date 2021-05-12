import React, { FC, useEffect } from 'react';
import { Layer, Rect, Stage, Group } from 'react-konva';
import Board from './board/Board';
import { ReactReduxContext, useDispatch, useSelector } from 'react-redux';
import { useWindowDimensions } from '../windowDimensions';
import { getRooms, moveBoard } from '../../features/board';
import { RootState } from '../../store';
import Agents from './players/Agents';
import Sidebar from './sidebar/Sidebar';
import { RouteComponentProps, useNavigate } from '@reach/router';
import { joinGame } from '../../features/game';
import { getPlayers } from '../../features/players';
import { getRoomStack } from '../../features/roomStack';
import DrawnCard from './cards/DrawnCard';
import { getDrawnCard } from '../../features/cardStacks';
import CharacterBar from './character/CharacterBar';
import { connect, disconnect } from '@giantmachines/redux-websocket/dist';
import { getLatestRoll } from '../../features/diceRolls';
import { getMonsters } from '../../features/monsters';
import Dice from './dice/Dice';
import SearchBar from './search/SearchBar';

const buildWebsocketUrl = (gameId: string) => {
  const httpRoot = process.env.REACT_APP_API_ROOT!!;
  const wsRoot = httpRoot.replace(/^http/, 'ws');
  return `${wsRoot}/games/${gameId}`;
};

interface GameProps extends RouteComponentProps {
  gameId?: string;
}

const Game: FC<GameProps> = ({ gameId }) => {
  const { width, height } = useWindowDimensions();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { x, y } = useSelector((state: RootState) => state.board.topLeft);

  useEffect(() => {
    if (!gameId || !/^[A-Z]{6}$/.test(gameId)) {
      navigate('/');
      return;
    }

    dispatch(joinGame(gameId));
    dispatch(connect(buildWebsocketUrl(gameId)));
    dispatch(getRooms());
    dispatch(getPlayers());
    dispatch(getMonsters());
    dispatch(getRoomStack());
    dispatch(getDrawnCard());
    dispatch(getLatestRoll());

    return () => {
      dispatch(disconnect());
    };
  }, [gameId, dispatch, navigate]);

  return (
    <div onContextMenu={(e) => e.preventDefault()}>
      <ReactReduxContext.Consumer>
        {(reduxContext) => (
          <Stage width={width} height={height}>
            <ReactReduxContext.Provider value={reduxContext}>
              <Layer
                draggable
                onDragEnd={(e) => {
                  dispatch(
                    moveBoard({
                      x: -e.target.x(),
                      y: -e.target.y(),
                    })
                  );
                }}
              >
                <Rect x={x} y={y} width={width} height={height} />
                <Board />
                <Agents />
                <Group name="overlay" />
              </Layer>
              <Layer>
                <Sidebar />
                <CharacterBar />
              </Layer>
            </ReactReduxContext.Provider>
          </Stage>
        )}
      </ReactReduxContext.Consumer>
      <DrawnCard />
      <Dice />
      <SearchBar />
    </div>
  );
};

export default Game;
