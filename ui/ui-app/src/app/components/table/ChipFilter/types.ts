export type SearchType = {
  type: "search";
  validate: (value: string) => boolean;
  errorMessage: string;
  chips: string[];
  onSearch: (value: string) => void;
  onRemoveChip: (value: string) => void;
  onRemoveGroup: () => void;
};
export type CheckboxType<T extends string | number> = {
  type: "checkbox";
  chips: string[];
  options: { [key in T]: string };
  onToggle: (value: T) => void;
  onRemoveChip: (value: T) => void;
  onRemoveGroup: () => void;
};
export type FilterType = SearchType | CheckboxType<any>;
