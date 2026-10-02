import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, QueryList, ViewChildren } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TagsService } from '../../../tags.service';
import { AutofocusDirective } from '../../../../ui/dynamic-input/autofocus.directive';
import { NgClass } from '@angular/common';
import { DynamicInputComponent } from '../../../../ui/dynamic-input/dynamic-input.component';
import { getSuggestions, Suggestion } from '../../../utils/getSuggestions';
import { DisplayLettersComponent } from '../../../../ui/display-letters/display-letters.component';
import { Wish } from '../../../../schemas/wish.schema';
import { initLetters } from '../../../utils/letters';


export type Tag = Wish['tags'][0]

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-tag',
  imports: [FormsModule, AutofocusDirective, DynamicInputComponent, DisplayLettersComponent, NgClass],
  templateUrl: './tag.component.html',
  styleUrl: './tag.component.css'
})
export class TagComponent implements OnDestroy {
  @Input({ required: true }) index!: number;
  @Input({ required: true }) tag!: Tag;
  @Input({ required: true }) doUpdateWish!: boolean;
  suggestions: Suggestion[] = [];
  selected_suggestion_index = -1;
  is_dropdown_opened = false;

  @Output() changeCustom = new EventEmitter<{ 'index': number, "tag": Tag, "doUpdateWish": boolean }>();

  tagInput!: ElementRef<HTMLDivElement>;
  @ViewChildren('suggestionRef', { read: ElementRef }) suggestionsRef!: QueryList<ElementRef<HTMLButtonElement>>;

  constructor(public TagsService: TagsService) { }

  recieveRef(ref: ElementRef<HTMLDivElement>) {
    this.tagInput = ref;
  }

  updateSuggestion() {
    this.suggestions = getSuggestions(this.tag.val, this.TagsService.getTags)
    this.selected_suggestion_index = -1;
  }
  onInputDoOpenDropdownAndUpdateSuggestion() {
    this.is_dropdown_opened = true;
    this.updateSuggestion();
  }
  onChangeDoUpdateSuggestionAndEmitChange() {
    this.changeCustom.emit({ 'index': this.index, "tag": this.tag, "doUpdateWish": this.doUpdateWish });
    this.updateSuggestion();
  }

  idTimeout: number | null = null;
  onFocusDoOpenDropdown() {
    // wrapped by setTimeout to avoid ExpressionChangedAfterItHasBeenCheckedError
    this.idTimeout = window.setTimeout(() => {
      this.is_dropdown_opened = true;
    }, 0);
    this.updateSuggestion();
  }
  ngOnDestroy() {
    if (this.idTimeout) {
      window.clearTimeout(this.idTimeout);
    }
  }
  onBlurDoCloseDropdownAndCallChange() {
    this.is_dropdown_opened = false;
    this.onChangeDoUpdateSuggestionAndEmitChange();
  }
  onDeleteDoTagEmptyAndCallChange() {
    this.tag.val = "";
    this.tag.letters = [];
    this.onChangeDoUpdateSuggestionAndEmitChange();
  }
  onCickSuggestionDoUpdateTagAndCallChange(index: number) {
    this.tag.val = this.suggestions[index].tag;
    this.tag.letters = initLetters(this.tag.val);
    this.onChangeDoUpdateSuggestionAndEmitChange();
  }

  onKeyDown(event: KeyboardEvent): void {
    if (!['Tab', 'ArrowDown', 'ArrowUp', 'Enter'].includes(event.key)) {
      return;
    }
    if (event.key == 'Enter') {
      if (this.selected_suggestion_index != -1) {
        this.tag.val = this.suggestions[this.selected_suggestion_index].tag;
        this.tag.letters = initLetters(this.tag.val);
        this.onChangeDoUpdateSuggestionAndEmitChange();
      }
      else {
        this.tagInput.nativeElement.blur();
        this.onBlurDoCloseDropdownAndCallChange();
      }
      return;
    }

    switch (event.key) {

      case 'Tab':
        if (!this.is_dropdown_opened) {
          break;
        }
        if (event.shiftKey) {
          this.selected_suggestion_index--;
        } else {
          this.selected_suggestion_index++;
        }
        event.preventDefault();
        break
      case 'ArrowDown':
        this.selected_suggestion_index++;
        break;
      case 'ArrowUp':
        this.selected_suggestion_index--;
        break;
    }

    if (this.selected_suggestion_index < 0) {
      this.selected_suggestion_index = this.suggestions.length - 1;
    }
    else if (this.selected_suggestion_index >= this.suggestions.length) {
      this.selected_suggestion_index = 0;
    }
    if (document.activeElement) {
      this.suggestionsRef.get(this.selected_suggestion_index)?.nativeElement
        .scrollIntoView({
          block: 'nearest'
        });
    }
  }
}
