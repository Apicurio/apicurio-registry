export const useWindow: () => Window = (): Window => {
    const w = window;
    return w as Window;
};
