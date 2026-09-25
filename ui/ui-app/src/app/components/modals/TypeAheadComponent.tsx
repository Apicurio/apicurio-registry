import { useEffect, useRef, useState } from 'react';
import {
  Select,
  SelectOption,
  SelectList,
  MenuToggle,
  MenuToggleElement,
  TextInputGroup,
  TextInputGroupMain,
  TextInputGroupUtilities,
  Button
} from '@patternfly/react-core';
import RhMicronsCloseIcon from '@patternfly/react-icons/dist/esm/icons/rh-microns-close-icon';

export type SelectOptionItem = {
  children: string;
  value: string;
};

export type SelectTypeaheadCreatableProps = {
  id?: string;
  'data-testid'?: string;
  name: string
  autoCompleteResults: string[] | undefined;
  value?: string;
  validated?: "default" | "success" | "error";
  onTextInputChanged: (value: string) => void;
  onSelectOption?: (value: string) => void;

};

export const SelectTypeaheadCreatable: React.FunctionComponent<SelectTypeaheadCreatableProps> = ({
  id,
  'data-testid': dataTestId,
  name = '',
  autoCompleteResults,
  value = '',
  validated = 'default',
  onTextInputChanged,
  onSelectOption,
}) => {

  const [isOpen, setIsOpen] = useState(false);
  const [selected, setSelected] = useState<string>('');
  const [inputValue, setInputValue] = useState<string>('');
  const [filterValue, setFilterValue] = useState<string>('');


  const [selectOptions, setSelectOptions] = useState<SelectOptionItem[]>([]);

  const [focusedItemIndex, setFocusedItemIndex] = useState<number | null>(null);
  const [activeItemId, setActiveItemId] = useState<string | null>(null);
  const textInputRef = useRef<HTMLInputElement>(null);

  const CREATE_NEW = 'create';


  useEffect(() => {
    const rawResults = autoCompleteResults ?? [];
    let newSelectOptions: SelectOptionItem[] = rawResults.map((option) => ({
      children: option,
      value: option,
    }));


    if (filterValue) {
      const filtered = rawResults.filter((result) =>
        String(result).toLowerCase().includes(filterValue.toLowerCase())
      );

      newSelectOptions = filtered.map((result) => ({ children: result, value: result }));


      if (!rawResults.some((option) => option === filterValue)) {
        newSelectOptions = [
          ...newSelectOptions,
          { children: `Create new option "${filterValue}"`, value: CREATE_NEW },
        ];
      }

      if (!isOpen) {
        setIsOpen(true);
      }
    }

    setSelectOptions(newSelectOptions);
  }, [autoCompleteResults, filterValue]);

  const createItemId = (value: string) => `select-typeahead-${value.replace(/\s+/g, '-')}`;

  const setActiveAndFocusedItem = (itemIndex: number) => {
    setFocusedItemIndex(itemIndex);
    const focusedItem = selectOptions?.[itemIndex];
    if (focusedItem) {
      setActiveItemId(createItemId(focusedItem.value));
    }
  };

  const resetActiveAndFocusedItem = () => {
    setFocusedItemIndex(null);
    setActiveItemId(null);
  };

  const closeMenu = () => {
    setIsOpen(false);
    resetActiveAndFocusedItem();
  };

  const onInputClick = () => {
    if (!isOpen) {
      setIsOpen(true);
    } else if (!inputValue) {
      closeMenu();
    }
  };

  const selectOption = (value: string, content: string) => {
    setInputValue(content);
    setFilterValue('');
    setSelected(value);
    closeMenu();
  };

  const onSelect = (_event: React.MouseEvent<Element, MouseEvent> | undefined, value: string | number | undefined) => {
    if (value !== undefined) {


      const stringValue = String(value);
      let finalValue = stringValue;

      if (stringValue === CREATE_NEW) {
        finalValue = filterValue;
      } else {
        const foundOption = selectOptions?.find((option) => option.value === stringValue);
        if (foundOption) {
          finalValue = foundOption.children;
        }
      }

      setSelected(finalValue);
      setInputValue(finalValue);
      setFilterValue('');
      closeMenu();

      onSelectOption?.(finalValue);
    }
  };

  const onTextInputChange = (_event: React.FormEvent<HTMLInputElement>, value: string) => {
    setInputValue(value);
    setFilterValue(value);
    onTextInputChanged(value);

    resetActiveAndFocusedItem();

    if (value !== selected) {
      setSelected('');
    }
  };

  const handleMenuArrowKeys = (key: string) => {
    const optionsLength = selectOptions?.length ?? 0;
    if (optionsLength === 0) return;

    if (!isOpen) {
      setIsOpen(true);
      return;
    }

    let indexToFocus = focusedItemIndex ?? 0;

    if (key === 'ArrowUp') {
      if (focusedItemIndex === null || focusedItemIndex <= 0) {
        indexToFocus = optionsLength - 1;
      } else {
        indexToFocus = focusedItemIndex - 1;
      }
    } else if (key === 'ArrowDown') {
      if (focusedItemIndex === null || focusedItemIndex >= optionsLength - 1) {
        indexToFocus = 0;
      } else {
        indexToFocus = focusedItemIndex + 1;
      }
    }

    setActiveAndFocusedItem(indexToFocus);
  };

  const onInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    const focusedItem = focusedItemIndex !== null ? selectOptions?.[focusedItemIndex] : null;

    switch (event.key) {
      case 'Enter':
        if (isOpen && focusedItem) {
          onSelect(undefined, focusedItem.value);
        }
        if (!isOpen) {
          setIsOpen(true);
        }
        break;
      case 'ArrowUp':
      case 'ArrowDown':
        event.preventDefault();
        handleMenuArrowKeys(event.key);
        break;
    }
  };

  const onToggleClick = () => {
    setIsOpen(!isOpen);
    textInputRef?.current?.focus();
  };

  const onClearButtonClick = () => {
    setSelected('');
    setInputValue('');
    setFilterValue('');
    resetActiveAndFocusedItem();
    textInputRef?.current?.focus();
  };

  const toggle = (toggleRef: React.Ref<MenuToggleElement>) => (
    <MenuToggle
      ref={toggleRef}
      variant="typeahead"
      aria-label="Typeahead creatable menu toggle"
      onClick={onToggleClick}
      isExpanded={isOpen}
      isFullWidth
      status={validated === 'error' ? 'danger' : 'success'}
    >
      <TextInputGroup isPlain>
        <TextInputGroupMain
          id={id ?? "create-typeahead-select-input"}
          data-testid={dataTestId}
          value={inputValue}
          onClick={onInputClick}
          onChange={onTextInputChange}
          onKeyDown={onInputKeyDown}
          autoComplete="off"
          innerRef={textInputRef}
          placeholder="Select or type..."
          {...(activeItemId && { 'aria-activedescendant': activeItemId })}
          role="combobox"
          isExpanded={isOpen}
          aria-controls="select-create-typeahead-listbox"
        />

        <TextInputGroupUtilities {...(!inputValue ? { style: { display: 'none' } } : {})}>
          <Button
            variant="plain"
            onClick={onClearButtonClick}
            aria-label="Clear input value"
            icon={<RhMicronsCloseIcon />}
          />
        </TextInputGroupUtilities>
      </TextInputGroup>
    </MenuToggle>
  );

  return (
    <Select
      id="create-typeahead-select"
      isOpen={isOpen}
      selected={selected}
      onSelect={onSelect}
      onOpenChange={(isOpen) => {
        !isOpen && closeMenu();
      }}
      toggle={toggle}
      variant="typeahead"
    >
      <SelectList id="select-create-typeahead-listbox">
        {selectOptions?.map((option, index) => (
          <SelectOption
            key={option.value}
            value={option.value}
            isFocused={focusedItemIndex === index}
            id={createItemId(option.value)}
          >
            {option.children}
          </SelectOption>
        ))}
      </SelectList>
    </Select>
  );
};