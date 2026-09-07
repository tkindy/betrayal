import { FC, useEffect } from 'react';
import { Layer, Rect, Stage, Group } from 'react-konva';
import Board from './board/Board';
import { ReactReduxContext } from 'react-redux';
import { useWindowDimensions } from '../windowDimensions';
import { moveBoard } from '../../features/board';
import Agents from './players/Agents';
import Sidebar from './sidebar/Sidebar';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import DrawnCard from './cards/DrawnCard';
import CharacterBar from './character/CharacterBar';
import { useAppDispatch, useAppSelector } from '../../hooks';
import { joinGame, receiveGameMessage } from '../../features/actions';
import { connectToWebSocket } from '../webSocket';
import { AppDispatch } from '../../store';
import { useSend } from '../hooks';
import { isLobbyId } from '../lobby/Lobby';
import { setName } from '../../features/lobby';
import { GameUpdate } from '../../features/models';

const buildWebsocketUrl = (gameId: string) => {
  const httpRoot = process.env.REACT_APP_API_ROOT!!;
  const wsRoot = httpRoot.replace(/^http/, 'ws');
  return `${wsRoot}/games/${gameId}`;
};

const connectToGame = (gameId: string, name: string, dispatch: AppDispatch) => {
  return connectToWebSocket(
    buildWebsocketUrl(gameId),
    dispatch,
    (message: GameUpdate) => receiveGameMessage({ name, update: message }),
    (webSocket) => {
      webSocket.send(JSON.stringify({ type: 'name', name }));
    }
  );
};

const Game: FC<{}> = () => {
  const { gameId } = useParams();
  const { width, height } = useWindowDimensions();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { x, y } = useAppSelector((state) => state.board.topLeft);
  const [send, setSend] = useSend();
  const name = useAppSelector((state) => state.lobby.name);
  const [searchParams] = useSearchParams();

  useEffect(() => {
    if (!isLobbyId(gameId)) {
      navigate('/');
      return;
    }
    if (name) {
      return;
    }

    let nameToUse: string | null = null;

    if (process.env.NODE_ENV === 'production') {
      nameToUse = localStorage.getItem(gameId);
    } else if (process.env.NODE_ENV === 'development') {
      nameToUse = searchParams.get('name');
    }

    if (nameToUse) {
      dispatch(setName(gameId, nameToUse, false));
    }
  }, [gameId, dispatch, navigate, name, searchParams]);
  useEffect(() => {
    if (!isLobbyId(gameId)) {
      navigate('/');
      return;
    }

    if (name) {
      dispatch(joinGame({ gameId }));
      const { send, close } = connectToGame(gameId, name, dispatch);
      setSend(send);

      return () => {
        setSend();
        close();
      };
    }
  }, [gameId, navigate, name, dispatch, setSend]);

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
            </ReactReduxContext.Provider>
          </Stage>
        )}
      </ReactReduxContext.Consumer>
      <Sidebar />
      <CharacterBar />
      <DrawnCard />
    </div>
  );
};

export default Game;
