import { AfterViewInit, ChangeDetectionStrategy, Component, Output, Input, EventEmitter, ElementRef, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutofocusDirective } from './autofocus.directive';
import { NgClass } from '@angular/common';
import { Letter } from '../../schemas/wish.schema';
import { DisplayLettersComponent } from "../display-letters/display-letters.component";
import { initLetters } from '../../app/utils/letters';


/**
 * An input whose width adapts to its content
 * 
 * Implementation details: 
 * Two elements, because each one has what the other lacks
 * + #InputRef shows the text , text that can be highlighted.
 * and stays in normal flow, so the box takes its size from that text.
 * + A textarea cannot paint those spans, and its own box does not follow its content.
 *
 * The textarea is the editor: caret, selection, keyboard, ngModel.
 * It sits position:absolute over #InputRef, with color:transparent, so only the caret shows.
 *
 * The single-line input uses the same pair so the width can hug the text.
 * Here the div has a fixed width and wraps, so the box grows in height.
 * 
 * In a way it reimplements an editable div. 
 * But an editable div inserts a lot of waste in the text, like <br> and <p> tags.
 * 
 * USAGE :
 *  
 *   <app-dynamic-input [(inputModel)]="value" />
 * 
 *   <app-dynamic-input [(inputModel)]="value" (change)="onChange(...)" />
 * 
 *   <app-dynamic-input 
 *     [(inputModel)]="tag" 
 *     [is_tag]="true"
 *     [is_autofocus]="tag === ''"
 *     (ref)="recieveRef($event)"
 *     (change)="onChange()" (focus)="onFocus()" (blur)="onBlur()" (input)="onInput()"
 *     (keydown)="onKeyDown($event)"    
 *   />
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-dynamic-text-area',
  imports: [FormsModule, AutofocusDirective, NgClass, DisplayLettersComponent],
  templateUrl: './dynamic-text-area.component.html',
  styleUrl: './dynamic-text-area.component.css'
})
export class DynamicTextAreaComponent implements AfterViewInit {

  @Input({required:true}) inputModel!: {val:string, letters:Letter[]} ;
  @Input() input_id = "";
  @Input() is_autofocus = false;
  @Input() is_tag = false;
  @Input() is_comment = false;
  @Input() highlightedHtml: string | null = null;

  @Output() inputModelChange = new EventEmitter<{val:string, letters:Letter[]}>();
  @Output() ref = new EventEmitter<ElementRef>();
  @Output() focus = new EventEmitter<never>();
  @Output() blur = new EventEmitter<never>();
  

  @ViewChild('InputRef') InputRef! : ElementRef<HTMLDivElement> ;
  @ViewChild('textAreatRef') textAreatRef! : ElementRef<HTMLTextAreaElement> ;


  ngAfterViewInit() {
    this.ref.emit(this.InputRef) ;
    // the visible size comes from the letters div; the textarea does not grow on its own
    this.textAreatRef.nativeElement.style.height = this.InputRef.nativeElement.style.height 
    this.textAreatRef.nativeElement.style.width = this.InputRef.nativeElement.style.width
 
  }

  onInputDoSync() {
    this.inputModel.letters = initLetters(this.inputModel.val)
    // keep the transparent textarea as tall as the text just typed
    this.textAreatRef.nativeElement.style.height  = this.InputRef.nativeElement.style.height
  }
  
  onSubmitDoPreventDefault(event:Event) {
    event.preventDefault() ;
  }

  onFocusDoEmit() {
    this.focus.emit();
  } 

  onBlurDoEmit() {
    this.blur.emit();
  } 
}