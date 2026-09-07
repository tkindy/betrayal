import { createSlice } from '@reduxjs/toolkit';

const minGridSize = 50;
const maxGridSize = 300;
const zoomStep = 50;

interface ZoomState {
  gridSize: number;
}

const initialState: ZoomState = { gridSize: 200 };

const zoomSlice = createSlice({
  name: 'zoom',
  initialState,
  reducers: {
    zoomIn(state) {
      state.gridSize = Math.min(maxGridSize, state.gridSize + zoomStep);
    },
    zoomOut(state) {
      state.gridSize = Math.max(minGridSize, state.gridSize - zoomStep);
    },
  },
});

export const { zoomIn, zoomOut } = zoomSlice.actions;
export default zoomSlice.reducer;
