import { FunctionComponent, ReactNode } from 'react';
import { Portal } from 'react-konva-utils';

interface PortalProps {
  selector: string;
  enabled: boolean;
  children: ReactNode;
}
type OverlayPortalProps = Omit<PortalProps, 'selector'>;

const OverlayPortal: FunctionComponent<OverlayPortalProps> = ({
  children,
  enabled,
}) => {
  return (
    <Portal selector=".overlay" enabled={enabled}>
      {children}
    </Portal>
  );
};

export default OverlayPortal;
