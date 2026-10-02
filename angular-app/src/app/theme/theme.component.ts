import { ChangeDetectionStrategy, Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ThemeService, ThemeName } from './theme.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-theme',
  standalone: true,
  imports: [FormsModule],
  templateUrl:'./theme.component.html',
  styleUrl: './theme.component.css'
})
export class ThemeComponent implements OnInit {
  currentTheme: ThemeName = 'light';

  constructor(
    public themeService: ThemeService,
    private cdr: ChangeDetectorRef
  ) {}
  
  ngOnInit() {
    this.currentTheme = this.themeService.getCurrentTheme();
  }

  onThemeChangeDoUpdate(newTheme: ThemeName) {
    this.themeService.setTheme(newTheme);
    this.currentTheme = newTheme;
  }
}