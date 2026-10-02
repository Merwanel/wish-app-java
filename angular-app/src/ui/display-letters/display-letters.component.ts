import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Letter } from '../../schemas/wish.schema';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-display-letters',
  imports: [NgClass],
  templateUrl: './display-letters.component.html',
  styleUrl: './display-letters.component.css',
})
export class DisplayLettersComponent {
  @Input() letters: Letter[] = [];
  /** When set (from ES `<mark>` highlights), preferred over letter spans. */
  @Input() highlightedHtml: string | null = null;

  constructor(private sanitizer: DomSanitizer) {}

  get safeHighlightedHtml(): SafeHtml | null {
    if (!this.highlightedHtml) {
      return null;
    }
    // ES highlight fragments are HTML-escaped content + <mark> tags only
    return this.sanitizer.bypassSecurityTrustHtml(this.highlightedHtml);
  }
}
