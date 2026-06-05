export type ButtonType = 'primary' | 'secondary' | 'ghost';
export interface ButtonProps {
  type?: ButtonType;
  disabled?: boolean;
  loading?: boolean;
}
