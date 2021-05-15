import { createSlice } from '@reduxjs/toolkit';
import { SearchResult } from './models';
import { createAppAsyncThunk } from './utils';
import * as api from '../api/api';
import { getGameId } from './selectors';

export const searchStacks = createAppAsyncThunk(
  'search/searchStacks',
  async ({ term }: { term: string }, { getState }) => {
    return api.searchStacks(getGameId(getState()), term);
  }
);

interface SearchState {
  results?: SearchResult[];
}

const initialState: SearchState = {};

const searchSlice = createSlice({
  name: 'search',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(searchStacks.fulfilled, (state, { payload: results }) => {
      state.results = results;
    });
  },
});

export default searchSlice.reducer;
