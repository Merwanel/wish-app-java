import { Injectable } from '@angular/core';

export type ThemeName = 'light' | 'gray' | 'dark' | 'blue' | 'chocolate' | 'solarized';

export interface Theme {
  name: ThemeName;
  displayName: string;
  className: string;
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private currentTheme: ThemeName = 'light';

  readonly availableThemes: Theme[] = [
    { name: 'light', displayName: 'Light', className: 'theme-light' },
    { name: 'gray', displayName: 'Gray', className: 'theme-gray' },
    { name: 'dark', displayName: 'Dark', className: 'theme-dark' },
    { name: 'blue', displayName: 'Ocean Blue', className: 'theme-blue' },
    { name: 'chocolate', displayName: 'Chocolate', className: 'theme-chocolate' },
    { name: 'solarized', displayName: 'Solarized', className: 'theme-solarized' }
  ];

  constructor() {
    const saved = localStorage.getItem('theme') as ThemeName;
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

    if (saved && this.isValidTheme(saved)) {
      this.setTheme(saved);
    } else {
      this.setTheme(prefersDark ? 'dark' : 'light');
    }
  }

  setTheme(themeName: ThemeName) {
    if (!this.isValidTheme(themeName)) return;

    this.availableThemes.forEach(theme => {
      document.body.classList.remove(theme.className);
    });

    // light colors already apply with no class on <body>
    if (themeName !== 'light') {
      const theme = this.availableThemes.find(t => t.name === themeName);
      if (theme) {
        document.body.classList.add(theme.className);
      }
    }

    this.currentTheme = themeName;
    localStorage.setItem('theme', themeName);
  }

  getCurrentTheme(): ThemeName {
    return this.currentTheme;
  }

  getCurrentThemeInfo(): Theme {
    return this.availableThemes.find(t => t.name === this.currentTheme) || this.availableThemes[0];
  }


  private isValidTheme(theme: ThemeName) {
    return this.availableThemes.some(t => t.name === theme);
  }
}