import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { Point } from '../components/geometry';

const minZoomLevel = 0;
const maxZoomLevel = 4;

interface ViewState {
  center: Point;
  zoomLevel: number;
}

const initialState: ViewState = {
  center: { x: 0, y: 0 },
  zoomLevel: 2,
};

const viewSlice = createSlice({
  name: 'view',
  initialState,
  reducers: {
    moveBoard(state, action: PayloadAction<Point>) {
      state.center = action.payload;
    },
    zoomIn(state) {
      state.zoomLevel = Math.min(maxZoomLevel, state.zoomLevel + 1);
    },
    zoomOut(state) {
      state.zoomLevel = Math.max(minZoomLevel, state.zoomLevel - 1);
    },
  },
});

export const { moveBoard, zoomIn, zoomOut } = viewSlice.actions;
export default viewSlice.reducer;
